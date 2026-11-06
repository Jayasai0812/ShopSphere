package com.shopsphere.controller;

import com.shopsphere.dto.ProductImageResponseDTO;
import com.shopsphere.service.ProductImageService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.net.URI;
import java.util.List;

/**
 * Admin product image management. Every route requires ROLE_ADMIN via
 * SecurityConfig. Upload is multipart/form-data ("file" part); reorder
 * takes the complete image-id order as JSON.
 */
@RestController
@RequestMapping("/api/v1/admin/products/{productId}/images")
public class ProductImageController {

        private final ProductImageService imageService;

        public ProductImageController(ProductImageService imageService) {
                this.imageService = imageService;
        }

        @GetMapping
        public ResponseEntity<List<ProductImageResponseDTO>> listImages(
                        @PathVariable Long productId) {
                return ResponseEntity.ok(imageService.listImages(productId));
        }

        @PostMapping
        public ResponseEntity<ProductImageResponseDTO> uploadImage(
                        @PathVariable Long productId,
                        @RequestPart("file") MultipartFile file) {
                ProductImageResponseDTO created = imageService.uploadImage(productId, file);
                return ResponseEntity.created(URI.create(
                                "/api/v1/admin/products/" + productId + "/images"))
                                .body(created);
        }

        /** Sets the given image as the product's single primary image. */
        @PatchMapping("/{imageId}/primary")
        public ResponseEntity<ProductImageResponseDTO> setPrimary(
                        @PathVariable Long productId, @PathVariable Long imageId) {
                return ResponseEntity.ok(imageService.setPrimary(productId, imageId));
        }

        /** Replaces the whole ordering: body is a JSON array of image ids. */
        @PatchMapping("/order")
        public ResponseEntity<List<ProductImageResponseDTO>> reorderImages(
                        @PathVariable Long productId,
                        @RequestBody List<Long> orderedIds) {
                return ResponseEntity.ok(imageService.reorderImages(productId, orderedIds));
        }

        @DeleteMapping("/{imageId}")
        public ResponseEntity<Void> deleteImage(@PathVariable Long productId,
                        @PathVariable Long imageId) {
                imageService.deleteImage(productId, imageId);
                return ResponseEntity.noContent().build();
        }
}
