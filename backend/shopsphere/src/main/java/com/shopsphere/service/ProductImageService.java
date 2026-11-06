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
                return imageRepository.findByProductIdOrderBySortOrderAsc(productId)
                                .stream().map(ProductImageService::toResponse).toList();
        }

        // ── upload ─────────────────────────────────────────────────────────

        @Transactional
        public ProductImageResponseDTO uploadImage(Long productId, MultipartFile file) {
                Product product = productRepository.findById(productId)
                                .orElseThrow(() -> new ResourceNotFoundException(
                                                "Product not found with id: " + productId));

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
                ProductImage image = new ProductImage();
                image.setProduct(product);
                image.setImageUrl(storageProperties.publicPrefix() + "/" + storedFileName);
                image.setSortOrder(existing.size());
                image.setPrimaryImage(!existing.isEmpty());
                image = imageRepository.save(image);

                return toResponse(image);
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
                        current.setPrimaryImage(false);
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
                        remaining.get(0).setPrimaryImage(true);
                }
                imageRepository.saveAll(remaining);

                deleteFileQuietly(storedFileName);
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
