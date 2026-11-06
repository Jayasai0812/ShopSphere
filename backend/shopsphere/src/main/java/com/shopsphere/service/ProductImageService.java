package com.shopsphere.service;

import com.shopsphere.dto.ProductImageResponseDTO;
import com.shopsphere.entity.Product;
import com.shopsphere.entity.ProductImage;
import com.shopsphere.exception.ResourceNotFoundException;
import com.shopsphere.repository.ProductImageRepository;
import com.shopsphere.repository.ProductRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Product image management (image-management phase).
 *
 * Responsibilities:
 * - upload: validate content type, extension, size and count, store the
 * binary OUTSIDE the database under {@link StorageProperties#getDir()},
 * and persist only metadata + public URL in MySQL.
 * - primary: first upload becomes primary automatically; at most one
 * primary image per product at any time.
 * - reorder: dense 0-based positions via image id list.
 * - delete: removes metadata row and the file from disk; promotes a new
 * primary if the deleted one was primary.
 */
@Service
public class ProductImageService {

        /** Hard limits — validated on upload. */
        public static final long MAX_FILE_SIZE_BYTES = 2L * 1024 * 1024; // 2 MB
        public static final int MAX_IMAGES_PER_PRODUCT = 10;

        private static final Set<String> ALLOWED_CONTENT_TYPES = Set.of(
                        "image/jpeg", "image/png", "image/webp");
        private static final Set<String> ALLOWED_EXTENSIONS = Set.of(
                        "jpg", "jpeg", "png", "webp");

        /** Decompression-bomb guard: no single dimension exceeds this. */
        private static final int MAX_DIMENSION_PIXELS = 8000;
        /** Decompression-bomb guard: total decoded pixels cap. */
        private static final long MAX_TOTAL_PIXELS = 24_000_000L; // ~24 MP

        private final ProductImageRepository imageRepository;
        private final ProductRepository productRepository;
        private final StorageProperties storageProperties;

        public ProductImageService(ProductImageRepository imageRepository,
                        ProductRepository productRepository,
                        StorageProperties storageProperties) {
                this.imageRepository = imageRepository;
                this.productRepository = productRepository;
                this.storageProperties = storageProperties;
        }

        // ── queries ────────────────────────────────────────────────────────

        @Transactional(readOnly = true)
        public List<ProductImageResponseDTO> listImages(Long productId) {
                requireProduct(productId);
                return imageRepository.findByProductIdOrderBySortOrderAsc(productId)
                                .stream().map(ProductImageService::toResponse).toList();
        }

        // ── upload ─────────────────────────────────────────────────────────

        @Transactional
        public ProductImageResponseDTO uploadImage(Long productId, MultipartFile file) {
                Product product = requireProduct(productId);

                validateFile(file);

                List<ProductImage> existing =
                                imageRepository.findByProductIdOrderBySortOrderAsc(productId);
                if (existing.size() >= MAX_IMAGES_PER_PRODUCT) {
                        throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                                        "Product cannot have more than "
                                                        + MAX_IMAGES_PER_PRODUCT + " images");
                }

                boolean makePrimary = existing.isEmpty();
                if (makePrimary) {
                        // Guarantee the unique primary constraint holds even if
                        // a previous partial state left one behind.
                        imageRepository.findByProductIdAndPrimaryImageTrue(productId)
                                        .ifPresent(imageRepository::delete);
                }

                String storedFileName = storeFile(file);
                try {
                        ProductImage image = new ProductImage();
                        image.setProduct(product);
                        image.setImageUrl(storageProperties.publicPrefix() + "/"
                                        + storedFileName);
                        image.setSortOrder(existing.size());
                        image.setPrimaryImage(existing.isEmpty() ? Boolean.TRUE : null);
                        image = imageRepository.save(image);
                        // Flush NOW so a constraint/DB failure surfaces inside this
                        // try-block — the catch below is the FIRST line of defense.
                        imageRepository.flush();

                        // SECOND line of defense: the actual commit happens AFTER
                        // this method returns. If the commit fails, the catch
                        // block never runs — so also delete the file in
                        // afterCompletion() whenever the transaction rolled back.
                        if (org.springframework.transaction.support.TransactionSynchronizationManager
                                        .isSynchronizationActive()) {
                                org.springframework.transaction.support.TransactionSynchronizationManager
                                                .registerSynchronization(
                                                                new org.springframework.transaction.support.TransactionSynchronization() {
                                                                        @Override
                                                                        public void afterCompletion(
                                                                                        int status) {
                                                                                if (status != org.springframework.transaction.support.TransactionSynchronization.STATUS_COMMITTED) {
                                                                                        deleteFileQuietly(storedFileName);
                                                                                }
                                                                        }
                                                                });
                        }
                        return toResponse(image);
                } catch (RuntimeException e) {
                        deleteFileQuietly(storedFileName);
                        throw e;
                }
        }

        // ── primary ────────────────────────────────────────────────────────

        @Transactional
        public ProductImageResponseDTO setPrimary(Long productId, Long imageId) {
                ProductImage target = ownedImage(productId, imageId);
                if (target.isPrimaryImage()) {
                        return toResponse(target);
                }
                ProductImage current = imageRepository
                                .findByProductIdAndPrimaryImageTrue(productId)
                                .orElse(null);
                if (current != null) {
                        // Flush BEFORE assigning the new primary: the composite
                        // unique (product_id, is_primary) forbids two TRUE rows,
                        // and Hibernate's dirty-check order is not guaranteed.
                        current.setPrimaryImage(null);
                        imageRepository.saveAndFlush(current);
                }
                target.setPrimaryImage(true);
                imageRepository.saveAndFlush(target);
                return toResponse(target);
        }

        // ── reorder ────────────────────────────────────────────────────────

        /**
         * Reorders images to exactly the given image-id order. Every image
         * of the product must appear exactly once — anything else is a 400
         * so a client can never silently drop or duplicate an image.
         */
        @Transactional
        public List<ProductImageResponseDTO> reorderImages(Long productId,
                        List<Long> orderedIds) {
                requireProduct(productId);
                List<ProductImage> images = imageRepository.findByProductIdOrderBySortOrderAsc(productId);
                if (orderedIds == null
                                || orderedIds.size() != images.size()
                                || !new HashSet<>(orderedIds).equals(imageIds(images))
                                || orderedIds.size() != new HashSet<>(orderedIds).size()) {
                        throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                                        "Reorder list must contain every image id of the "
                                                        + "product exactly once");
                }
                Map<Long, Integer> position = new HashMap<>();
                for (int i = 0; i < orderedIds.size(); i++) {
                        position.put(orderedIds.get(i), i);
                }
                for (ProductImage image : images) {
                        image.setSortOrder(position.get(image.getId()));
                }
                imageRepository.saveAll(images);
                return listImages(productId);
        }

        // ── delete ─────────────────────────────────────────────────────────

        @Transactional
        public void deleteImage(Long productId, Long imageId) {
                ProductImage image = ownedImage(productId, imageId);
                boolean wasPrimary = image.isPrimaryImage();
                String storedFileName = fileNameFromUrl(image.getImageUrl());

                imageRepository.delete(image);
                imageRepository.flush(); // release the primary unique slot first

                // Re-densify remaining order and keep exactly one primary.
                List<ProductImage> remaining = imageRepository
                                .findByProductIdOrderBySortOrderAsc(productId);
                for (int i = 0; i < remaining.size(); i++) {
                        remaining.get(i).setSortOrder(i);
                }
                if (wasPrimary && !remaining.isEmpty()) {
                        remaining.get(0).setPrimaryImage(Boolean.TRUE);
                }
                imageRepository.saveAll(remaining);

                // Physical deletion happens only AFTER the transaction commits:
                // if the commit rolls back, the row survives and so must its file.
                org.springframework.transaction.support.TransactionSynchronizationManager
                                .registerSynchronization(
                                                new org.springframework.transaction.support.TransactionSynchronization() {
                                                        @Override
                                                        public void afterCommit() {
                                                                deleteFileQuietly(storedFileName);
                                                        }
                                                });
        }

        // ── validation & storage helpers ───────────────────────────────────

        private void validateFile(MultipartFile file) {
                if (file == null || file.isEmpty()) {
                        throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                                        "Image file is required");
                }
                if (file.getSize() > MAX_FILE_SIZE_BYTES) {
                        throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                                        "Image must not exceed 2 MB");
                }
                String contentType = file.getContentType() == null
                                ? ""
                                : file.getContentType().toLowerCase(Locale.ROOT);
                if (!ALLOWED_CONTENT_TYPES.contains(contentType)) {
                        throw new ResponseStatusException(HttpStatus.UNSUPPORTED_MEDIA_TYPE,
                                        "Only JPEG, PNG and WebP images are allowed");
                }
                String original = file.getOriginalFilename() == null
                                ? ""
                                : file.getOriginalFilename().toLowerCase(Locale.ROOT);
                int dot = original.lastIndexOf('.');
                String extension = dot < 0 ? "" : original.substring(dot + 1);
                if (!ALLOWED_EXTENSIONS.contains(extension)) {
                        throw new ResponseStatusException(HttpStatus.UNSUPPORTED_MEDIA_TYPE,
                                        "Image file extension must be jpg, jpeg, png or webp");
                }
                // The declared extension must agree with the declared MIME type
                // (.png ↔ image/png, .jpg/.jpeg ↔ image/jpeg, .webp ↔ image/webp).
                boolean extensionMatches = switch (contentType) {
                        case "image/png" -> extension.equals("png");
                        case "image/jpeg" -> extension.equals("jpg")
                                        || extension.equals("jpeg");
                        case "image/webp" -> extension.equals("webp");
                        default -> false;
                };
                if (!extensionMatches) {
                        throw new ResponseStatusException(HttpStatus.UNSUPPORTED_MEDIA_TYPE,
                                        "File extension does not match the declared image type");
                }
                validateDecodableImage(file, contentType);
        }

        /**
         * Actual image-content validation: the bytes must decode as a FULL
         * image of the declared type. Metadata inspection alone (width/
         * height from the header) is not enough — a file with a valid PNG
         * header but corrupted IDAT payload is rejected here because
         * reader.read(0) throws. Uses the JDK ImageIO for JPEG/PNG and the
         * registered TwelveMonkeys WebP reader; also caps dimensions/total
         * pixels BEFORE decoding to guard against decompression bombs.
         */
        private void validateDecodableImage(MultipartFile file, String contentType) {
                try (InputStream in = file.getInputStream();
                                javax.imageio.stream.ImageInputStream stream =
                                                javax.imageio.ImageIO.createImageInputStream(in)) {
                        javax.imageio.ImageIO.setUseCache(false);
                        if (stream == null) {
                                throw new ResponseStatusException(HttpStatus.UNSUPPORTED_MEDIA_TYPE,
                                                "File is not a decodable image");
                        }
                        java.util.Iterator<javax.imageio.ImageReader> readers =
                                        javax.imageio.ImageIO.getImageReaders(stream);
                        if (!readers.hasNext()) {
                                throw new ResponseStatusException(HttpStatus.UNSUPPORTED_MEDIA_TYPE,
                                                "File is not a decodable image");
                        }
                        javax.imageio.ImageReader reader = readers.next();
                        // The decoded format must match the declared content type.
                        String readerMime = reader.getOriginatingProvider() == null
                                        ? null
                                        : reader.getOriginatingProvider().getMIMETypes()[0]
                                                        .toLowerCase(Locale.ROOT);
                        boolean typeMatches = switch (contentType) {
                                case "image/jpeg" -> "image/jpeg".equals(readerMime);
                                case "image/png" -> "image/png".equals(readerMime);
                                case "image/webp" -> readerMime != null
                                                && readerMime.contains("webp");
                                default -> false;
                        };
                        if (!typeMatches) {
                                throw new ResponseStatusException(HttpStatus.UNSUPPORTED_MEDIA_TYPE,
                                                "File content does not match the declared image type");
                        }
                        try {
                                reader.setInput(stream, true, true);
                                int width = reader.getWidth(0);
                                int height = reader.getHeight(0);
                                if (width <= 0 || height <= 0
                                                || width > MAX_DIMENSION_PIXELS
                                                || height > MAX_DIMENSION_PIXELS
                                                || (long) width * height > MAX_TOTAL_PIXELS) {
                                        throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                                                        "Image dimensions exceed the allowed limit");
                                }
                                // FULL decode: validates the complete pixel data,
                                // not just the header/metadata.
                                reader.read(0);
                        } finally {
                                reader.dispose();
                        }
                } catch (ResponseStatusException e) {
                        throw e;
                } catch (Exception e) {
                        throw new ResponseStatusException(HttpStatus.UNSUPPORTED_MEDIA_TYPE,
                                        "File is not a decodable image");
                }
        }

        /** Writes the binary to the storage directory OUTSIDE the database. */
        private String storeFile(MultipartFile file) {
                try {
                        Path dir = Path.of(storageProperties.dir());
                        Files.createDirectories(dir);
                        String original = file.getOriginalFilename() == null
                                        ? "image"
                                        : file.getOriginalFilename();
                        String extension = original.substring(
                                        Math.max(original.lastIndexOf('.'), 0))
                                        .toLowerCase(Locale.ROOT);
                        String storedFileName = "product-"
                                        + java.util.UUID.randomUUID().toString()
                                                        .replace("-", "")
                                        + extension;
                        Path target = dir.resolve(storedFileName).normalize();
                        // Path-traversal guard: resolved file must stay in dir.
                        if (!target.startsWith(dir)) {
                                throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                                                "Invalid storage location");
                        }
                        try (InputStream in = file.getInputStream()) {
                                Files.copy(in, target,
                                                StandardCopyOption.REPLACE_EXISTING);
                        }
                        return storedFileName;
                } catch (IOException e) {
                        throw new ResponseStatusException(
                                        HttpStatus.INTERNAL_SERVER_ERROR,
                                        "Could not store image file", e);
                }
        }

        private void deleteFileQuietly(String storedFileName) {
                if (storedFileName == null) {
                        return;
                }
                try {
                        Files.deleteIfExists(
                                        Path.of(storageProperties.dir()).resolve(storedFileName));
                } catch (IOException ignored) {
                        // File cleanup must never block metadata deletion.
                }
        }

        private ProductImage ownedImage(Long productId, Long imageId) {
                return imageRepository.findByIdAndProductId(imageId, productId)
                                .orElseThrow(() -> new ResourceNotFoundException(
                                                "Image not found with id: " + imageId
                                                                + " for product: " + productId));
        }

        /** 404 when the product does not exist (used by list/reorder/upload). */
        private Product requireProduct(Long productId) {
                return productRepository.findById(productId)
                                .orElseThrow(() -> new ResourceNotFoundException(
                                                "Product not found with id: " + productId));
        }

        private static Set<Long> imageIds(List<ProductImage> images) {
                Set<Long> ids = new HashSet<>();
                for (ProductImage image : images) {
                        ids.add(image.getId());
                }
                return ids;
        }

        private static String fileNameFromUrl(String url) {
                int slash = url.lastIndexOf('/');
                return slash < 0 ? url : url.substring(slash + 1);
        }

        public static ProductImageResponseDTO toResponse(ProductImage image) {
                return new ProductImageResponseDTO(image.getId(), image.getImageUrl(),
                                image.getSortOrder(), image.isPrimaryImage());
        }
}
