package com.shopsphere.controller;

import com.shopsphere.dto.ProductResponseDTO;
import com.shopsphere.dto.ProductSearchCriteria;
import com.shopsphere.dto.ProductSearchCriteriaParser;
import com.shopsphere.service.ProductService;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
import java.util.List;

/**
 * Product discovery endpoints (TASK-019).
 *
 * GET /api/v1/products supports optional search/filter/sort parameters —
 * all validated and aliased in {@link ProductSearchCriteriaParser}; raw
 * property names never reach the persistence layer. Without parameters it
 * still serves the public listing, which now excludes INACTIVE products.
 *
 * Admin listing/details live under /api/v1/admin/products (ROLE_ADMIN)
 * and DO include inactive products with their status visible.
 */
@RestController
@RequestMapping("/api/v1/products")
public class ProductController {

        private final ProductService productService;

        public ProductController(ProductService productService) {
                this.productService = productService;
        }

        /**
         * Public discovery: q, categoryId, minPrice/maxPrice, minRating,
         * available, minStock, sort aliases (name/price/newest/rating/
         * popularity) and standard page/size params. Public results are
         * always active-only.
         *
         * Page/size are bound as plain ints (NOT via the Spring Pageable
         * resolver): the alias sort values (newest/rating/popularity) are
         * not real Product properties, so letting the resolver also bind
         * ?sort=... would fail with a 400 before our parser sees them.
         */
        @GetMapping
        public ResponseEntity<Page<ProductResponseDTO>> searchProducts(
                        @RequestParam(required = false) String q,
                        @RequestParam(required = false) Long categoryId,
                        @RequestParam(required = false) String minPrice,
                        @RequestParam(required = false) String maxPrice,
                        @RequestParam(required = false) String minRating,
                        @RequestParam(required = false) String available,
                        @RequestParam(required = false) String minStock,
                        @RequestParam(required = false) List<String> sort,
                        @RequestParam(defaultValue = "0") int page,
                        @RequestParam(defaultValue = "10") int size) {

                List<String> sortParams = sort;
                // Spring splits "price,asc" into [price, asc] for List params;
                // re-join direction tokens that follow an alias so the parser
                // sees the original "alias,direction" pairs.
                if (sort != null && sort.size() > 1) {
                        sortParams = new ArrayList<>();
                        for (int i = 0; i < sort.size(); i++) {
                                String token = sort.get(i);
                                boolean isDirection = token.equalsIgnoreCase("asc")
                                                || token.equalsIgnoreCase("desc");
                                boolean prevIsAlias = i > 0 && ProductSearchCriteria.SortKey
                                                .from(sort.get(i - 1)) != null;
                                if (isDirection && prevIsAlias && !sortParams.isEmpty()) {
                                        sortParams.set(sortParams.size() - 1,
                                                        sortParams.get(sortParams.size() - 1) + "," + token);
                                } else {
                                        sortParams.add(token);
                                }
                        }
                }

                if (page < 0 || size < 1) {
                        throw new org.springframework.web.server.ResponseStatusException(
                                        org.springframework.http.HttpStatus.BAD_REQUEST,
                                        "Page must be non-negative and size must be at least 1");
                }
                ProductSearchCriteria criteria = ProductSearchCriteriaParser.parse(
                                q, categoryId, minPrice, maxPrice, minRating,
                                available, minStock, sortParams, size);
                return ResponseEntity.ok(productService.searchProducts(criteria,
                                PageRequest.of(Math.max(page, 0), Math.max(size, 1))));
        }

        /**
         * Public product details: inactive products return 404 here (never
         * publicly accessible), exactly like unknown ones.
         */
        @GetMapping("/{id}")
        public ResponseEntity<ProductResponseDTO> getProductById(@PathVariable Long id) {
                return ResponseEntity.ok(productService.getProductById(id));
        }

        /**
         * Backward-compatible public category listing — now active-only,
         * delegating to the same specification path as search.
         */
        @GetMapping("/category/{categoryId}")
        public ResponseEntity<List<ProductResponseDTO>> getProductsByCategory(
                        @PathVariable Long categoryId) {
                return ResponseEntity.ok(productService.getProductsByCategory(categoryId));
        }
}
