package com.shopsphere.controller;

import com.shopsphere.dto.ProductRequestDTO;
import com.shopsphere.dto.ProductResponseDTO;
import com.shopsphere.dto.ProductUpdateRequestDTO;
import com.shopsphere.service.ProductService;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Admin product management (TASK-019). All routes require ROLE_ADMIN via
 * SecurityConfig. Unlike the public endpoints, listings INCLUDE inactive
 * (soft-deleted) products with their status visible, and inactive product
 * details are accessible.
 */
@RestController
@RequestMapping("/api/v1/admin/products")
public class AdminProductController {

        private final ProductService productService;

        public AdminProductController(ProductService productService) {
                this.productService = productService;
        }

        /** Full catalog listing including INACTIVE products. */
        @GetMapping
        public ResponseEntity<Page<ProductResponseDTO>> getAllProducts(
                        @PageableDefault(size = 10, sort = "id") Pageable pageable) {
                return ResponseEntity.ok(productService.getAllProductsForAdmin(pageable));
        }

        /** Admin detail: inactive products are visible. */
        @GetMapping("/{id}")
        public ResponseEntity<ProductResponseDTO> getProductById(@PathVariable Long id) {
                return ResponseEntity.ok(productService.getProductByIdForAdmin(id));
        }

        /** Category listing including inactive products. */
        @GetMapping("/category/{categoryId}")
        public ResponseEntity<List<ProductResponseDTO>> getProductsByCategory(
                        @PathVariable Long categoryId) {
                return ResponseEntity.ok(productService.getProductsByCategoryForAdmin(categoryId));
        }

        /** Creates a product (admin-only catalog write). */
        @PostMapping
        public ResponseEntity<ProductResponseDTO> createProduct(
                        @Valid @RequestBody ProductRequestDTO request) {
                var created = productService.createProduct(request);
                return ResponseEntity.created(
                                java.net.URI.create("/api/v1/admin/products/" + created.id()))
                                .body(created);
        }

        /** Full update of a product. */
        @PutMapping("/{id}")
        public ResponseEntity<ProductResponseDTO> updateProduct(
                        @PathVariable Long id,
                        @Valid @RequestBody ProductUpdateRequestDTO request) {
                return ResponseEntity.ok(productService.updateProduct(id, request));
        }

        /** Activates/deactivates a product (soft delete / restore). */
        @PatchMapping("/{id}/status")
        public ResponseEntity<ProductResponseDTO> setProductStatus(
                        @PathVariable Long id, @RequestParam boolean active) {
                return ResponseEntity.ok(productService.setProductStatus(id, active));
        }

        /** Soft-deletes a product by deactivating it. */
        @DeleteMapping("/{id}")
        public ResponseEntity<Void> deleteProduct(@PathVariable Long id) {
                productService.deleteProduct(id);
                return ResponseEntity.noContent().build();
        }
}
