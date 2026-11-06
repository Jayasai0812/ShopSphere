package com.shopsphere.service;

import com.shopsphere.dto.ProductRequestDTO;
import com.shopsphere.dto.ProductResponseDTO;
import com.shopsphere.dto.ProductSearchCriteria;
import com.shopsphere.dto.ProductSearchCriteriaParser;
import com.shopsphere.dto.ProductUpdateRequestDTO;
import com.shopsphere.dto.RatingSummaryDTO;
import com.shopsphere.entity.Inventory;
import com.shopsphere.entity.Product;
import com.shopsphere.exception.DuplicateSkuException;
import com.shopsphere.exception.ResourceNotFoundException;
import com.shopsphere.repository.CategoryRepository;
import com.shopsphere.repository.InventoryRepository;
import com.shopsphere.repository.ProductRepository;
import com.shopsphere.repository.ProductSpecifications;
import com.shopsphere.repository.UserRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Service
@Transactional
public class ProductService {

    private final ProductRepository productRepository;
    private final CategoryRepository categoryRepository;
    private final UserRepository userRepository;
    private final InventoryRepository inventoryRepository;
    private final com.shopsphere.service.ReviewService reviewService;

    public ProductService(ProductRepository productRepository,
                          CategoryRepository categoryRepository,
                          UserRepository userRepository,
                          InventoryRepository inventoryRepository,
                          com.shopsphere.service.ReviewService reviewService) {
        this.productRepository = productRepository;
        this.categoryRepository = categoryRepository;
        this.userRepository = userRepository;
        this.inventoryRepository = inventoryRepository;
        this.reviewService = reviewService;
    }

    public ProductResponseDTO createProduct(ProductRequestDTO request) {
        var category = categoryRepository.findById(request.categoryId())
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Category not found with id: " + request.categoryId()));

        var seller = userRepository.findById(request.sellerId())
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Seller not found with id: " + request.sellerId()));

        Product product = new Product();
        product.setName(request.name());
        product.setDescription(request.description());
        product.setSku(request.sku());
        product.setPrice(request.price());
        product.setCategory(category);
        product.setSeller(seller);
        product.setActive(true);

        if (productRepository.findBySku(request.sku()).isPresent()) {
            throw new DuplicateSkuException(request.sku());
        }

        Inventory inventory = new Inventory();
        inventory.setProduct(product);
        inventory.setStockQuantity(request.stockQuantity());
        product.setInventory(inventory);

        Product saved = productRepository.save(product);
        return toResponse(saved, RatingSummaryDTO.empty(saved.getId()),
                request.stockQuantity());
    }

    public ProductResponseDTO updateProduct(Long id, ProductUpdateRequestDTO request) {
        Product product = productRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Product not found with id: " + id));

        productRepository.findBySku(request.sku())
                .filter(existing -> !existing.getId().equals(id))
                .ifPresent(existing -> { throw new DuplicateSkuException(request.sku()); });

        var category = categoryRepository.findById(request.categoryId())
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Category not found with id: " + request.categoryId()));

        product.setName(request.name());
        product.setDescription(request.description());
        product.setSku(request.sku());
        product.setPrice(request.price());
        product.setCategory(category);

        if (product.getInventory() != null) {
            product.getInventory().setStockQuantity(request.stockQuantity());
        }

        return toResponse(productRepository.save(product),
                reviewService.summarize(id), request.stockQuantity());
    }

    public ProductResponseDTO setProductStatus(Long id, boolean active) {
        Product product = productRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Product not found with id: " + id));
        product.setActive(active);
        return toResponse(productRepository.save(product),
                reviewService.summarize(id), stockOf(product));
    }

    public void deleteProduct(Long id) {
        Product product = productRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Product not found with id: " + id));
        product.setActive(false);
        productRepository.save(product);
    }

    /**
     * Public product discovery (TASK-019): search + filter + sort with
     * exact pagination.
     *
     * Every public query starts with active = true (soft-deleted/deactivated
     * products are never publicly visible). Filters compose via
     * {@link ProductSpecifications}; rating and popularity use correlated
     * subqueries so pagination counts remain exact (one row per product in
     * the outer query).
     *
     * Stock values for the response are fetched in ONE grouped query over
     * inventory for the page's product ids — no N+1, and no dependence on
     * lazy inventory loading inside the mapping loop.
     */
    @Transactional(readOnly = true)
    public Page<ProductResponseDTO> searchProducts(ProductSearchCriteria criteria,
                    Pageable pageable) {
            List<org.springframework.data.jpa.domain.Specification<Product>> specs =
                            new ArrayList<>();
            specs.add(ProductSpecifications.isActive());
            if (criteria.q() != null) {
                    specs.add(ProductSpecifications.textSearch(criteria.q()));
            }
            if (criteria.categoryId() != null) {
                    if (!categoryRepository.existsById(criteria.categoryId())) {
                            throw new ResourceNotFoundException("Category not found with id: "
                                            + criteria.categoryId());
                    }
                    specs.add(ProductSpecifications.inCategory(criteria.categoryId()));
            }
            if (criteria.minPrice() != null) {
                    specs.add(ProductSpecifications.priceAtLeast(criteria.minPrice()));
            }
            if (criteria.maxPrice() != null) {
                    specs.add(ProductSpecifications.priceAtMost(criteria.maxPrice()));
            }
            if (criteria.minRating() != null) {
                    specs.add(ProductSpecifications.withMinRating(criteria.minRating()));
            }
            if (criteria.available() != null) {
                    specs.add(ProductSpecifications.withAvailability(criteria.available()));
            }
            if (criteria.minStock() != null) {
                    specs.add(ProductSpecifications.withMinStock(criteria.minStock()));
            }

            org.springframework.data.jpa.domain.Specification<Product> spec =
                            ProductSpecifications.and(specs);

            Pageable effectivePageable = withCappedSize(pageable);
            // Alias-based sorting: the controller passes a Pageable WITHOUT the
            // raw ?sort values (they are not real Product properties). Here the
            // validated aliases are mapped to real columns / subquery
            // expressions, always ending with the id tie-breaker.
            List<ProductSearchCriteria.SortSpec> sorts = criteria.sorts();
            boolean needsExpressionSort = sorts.stream().anyMatch(s ->
                            s.key() == ProductSearchCriteria.SortKey.RATING
                                            || s.key() == ProductSearchCriteria.SortKey.POPULARITY);

            if (!needsExpressionSort) {
                    Page<Product> page = productRepository.findAll(spec,
                                    withCappedSize(withAliasSort(effectivePageable, sorts)));
                    return mapPage(page);
            }

            // Expression-sort path: the predicate is applied through a
            // dedicated Specification wrapper so the WHERE clause is honored,
            // and the ordering is set on the criteria query itself.
            Page<Product> page = productRepository.findAll(
                            (root, query, cb) -> {
                                    query.orderBy(expressionOrders(query, cb, root, sorts));
                                    return spec.toPredicate(root, query, cb);
                            },
                            PageRequest.of(effectivePageable.getPageNumber(),
                                            effectivePageable.getPageSize()));
            return mapPage(page);
    }

    /** Builds the expression ORDER BY (rating/popularity) + id tie-breaker. */
    private List<jakarta.persistence.criteria.Order> expressionOrders(
                    jakarta.persistence.criteria.CriteriaQuery<?> query,
                    jakarta.persistence.criteria.CriteriaBuilder cb,
                    jakarta.persistence.criteria.Root<Product> root,
                    List<ProductSearchCriteria.SortSpec> sorts) {
            List<jakarta.persistence.criteria.Order> orders = new ArrayList<>();
            for (ProductSearchCriteria.SortSpec sortSpec : sorts) {
                    switch (sortSpec.key()) {
                            case RATING -> {
                                    var avg = ProductSpecifications.approvedAverageRating(query, cb, root);
                                    // Unrated products (NULL) sort LAST, direction-free:
                                    // a null-marker (1 if null, else 0) sorts first, then
                                    // the average itself. Portable across H2/MySQL.
                                    var nullMarker = cb.selectCase()
                                                    .when(cb.isNull(avg), 1)
                                                    .otherwise(0);
                                    orders.add(cb.asc(nullMarker));
                                    orders.add(sortSpec.descending()
                                                    ? cb.desc(avg) : cb.asc(avg));
                            }
                            case POPULARITY -> {
                                    var units = ProductSpecifications.unitsSold(query, cb, root);
                                    orders.add(sortSpec.descending()
                                                    ? cb.desc(units) : cb.asc(units));
                            }
                            default -> {
                                    // NAME/PRICE/NEWEST are property sorts — handled in
                                    // withAliasSort; in the expression path they are
                                    // appended as property orders below.
                                    var path = switch (sortSpec.key()) {
                                            case NAME -> root.get("name");
                                            case PRICE -> root.get("price");
                                            case NEWEST -> root.get("createdAt");
                                            default -> null;
                                    };
                                    if (path != null) {
                                            orders.add(sortSpec.descending()
                                                            ? cb.desc(path) : cb.asc(path));
                                    }
                            }
                    }
            }
            orders.add(cb.asc(root.get("id")));
            return orders;
    }

    /**
     * Maps the validated sort aliases onto real property paths for the
     * simple (non-expression) aliases, honoring the requested direction.
     * Unsorted request → default name asc. The id tie-breaker is appended
     * for deterministic pagination.
     */
    private Pageable withAliasSort(Pageable pageable, List<ProductSearchCriteria.SortSpec> sorts) {
            Sort result = Sort.unsorted();
            boolean any = false;
            for (ProductSearchCriteria.SortSpec sortSpec : sorts) {
                    String property = switch (sortSpec.key()) {
                            case NAME -> "name";
                            case PRICE -> "price";
                            case NEWEST -> "createdAt";
                            default -> null;
                    };
                    if (property != null) {
                            result = result.and(Sort.by(
                                            sortSpec.descending() ? Sort.Direction.DESC : Sort.Direction.ASC,
                                            property));
                            any = true;
                    }
            }
            if (!any) {
                    result = Sort.by(Sort.Direction.ASC, "name");
            }
            if (result.stream().noneMatch(o -> o.getProperty().equals("id"))) {
                    result = result.and(Sort.by(Sort.Direction.ASC, "id"));
            }
            return PageRequest.of(pageable.getPageNumber(), pageable.getPageSize(), result);
    }

    /**
     * Deterministic pagination: appends the unique id as the final sort key
     * after whatever Pageable sort was requested, unless an id order is
     * already present. (Used by the admin listing and unfiltered public
     * listing; the search path uses withAliasSort instead.)
     */
    private Pageable withTieBreaker(Pageable pageable) {
            for (Sort.Order order : pageable.getSort()) {
                    if (order.getProperty().equals("id")) {
                            return pageable;
                    }
            }
            return PageRequest.of(pageable.getPageNumber(), pageable.getPageSize(),
                            pageable.getSort().and(Sort.by(Sort.Direction.ASC, "id")));
    }

    /** Public listing with NO filters: still active-only, still pageable. */
    @Transactional(readOnly = true)
    public Page<ProductResponseDTO> getAllProducts(Pageable pageable) {
            Page<Product> page = productRepository.findAll(
                            ProductSpecifications.isActive(),
                            withCappedSize(withTieBreaker(pageable)));
            return mapPage(page);
    }

    /** Admin listing: includes INACTIVE products, status visible. */
    @Transactional(readOnly = true)
    public Page<ProductResponseDTO> getAllProductsForAdmin(Pageable pageable) {
            Page<Product> page = productRepository.findAll(withCappedSize(pageable));
            return mapPage(page);
    }

    /**
     * Public product details: an INACTIVE product is not publicly
     * accessible — it returns 404, exactly like an unknown product, so
     * deactivated catalog entries don't leak to unauthenticated callers.
     */
    @Transactional(readOnly = true)
    public ProductResponseDTO getProductById(Long id) {
            Product product = productRepository.findById(id)
                            .filter(Product::isActive)
                            .orElseThrow(() -> new ResourceNotFoundException(
                                            "Product not found with id: " + id));
            return toResponse(product, reviewService.summarize(id),
                            stockOf(product));
    }

    /** Admin product details: inactive products ARE visible to admins. */
    @Transactional(readOnly = true)
    public ProductResponseDTO getProductByIdForAdmin(Long id) {
            Product product = productRepository.findById(id)
                            .orElseThrow(() -> new ResourceNotFoundException(
                                            "Product not found with id: " + id));
            return toResponse(product, reviewService.summarize(id),
                            stockOf(product));
    }

    /**
     * Public category listing: active-only. Kept as a backward-compatible
     * alias of the search path (same specification composition); still
     * returns 404 when the category itself doesn't exist.
     */
    @Transactional(readOnly = true)
    public List<ProductResponseDTO> getProductsByCategory(Long categoryId) {
            if (!categoryRepository.existsById(categoryId)) {
                    throw new ResourceNotFoundException("Category not found with id: " + categoryId);
            }
            List<Product> products = productRepository.findAll(
                            ProductSpecifications.isActive()
                                            .and(ProductSpecifications.inCategory(categoryId)));
            // ONE grouped query for the whole category's rating summaries (no N+1).
            var ratings = reviewService.summarizeAll(
                    products.stream().map(Product::getId).toList());
            return products.stream()
                    .map(p -> toResponse(p, ratings.get(p.getId()),
                                    stockOf(p)))
                    .toList();
    }

    /** Admin category listing includes inactive products. */
    @Transactional(readOnly = true)
    public List<ProductResponseDTO> getProductsByCategoryForAdmin(Long categoryId) {
            if (!categoryRepository.existsById(categoryId)) {
                    throw new ResourceNotFoundException("Category not found with id: " + categoryId);
            }
            List<Product> products = productRepository.findByCategoryId(categoryId);
            var ratings = reviewService.summarizeAll(
                    products.stream().map(Product::getId).toList());
            return products.stream()
                    .map(p -> toResponse(p, ratings.get(p.getId()),
                                    stockOf(p)))
                    .toList();
    }

    private Pageable withCappedSize(Pageable pageable) {
            if (pageable.getPageSize() > ProductSearchCriteriaParser.MAX_PAGE_SIZE) {
                    return org.springframework.data.domain.PageRequest.of(
                                    pageable.getPageNumber(), ProductSearchCriteriaParser.MAX_PAGE_SIZE,
                                    pageable.getSort());
            }
            return pageable;
    }

    /**
     * Maps a product page with batched lookups only: ONE grouped stock
     * query over the page's product ids plus the batched rating summaries
     * passed in. Avoids N+1 (and avoids relying on lazy inventory loads
     * inside the mapping loop).
     */
    private Page<ProductResponseDTO> mapPage(Page<Product> page) {
        List<Product> content = page.getContent();
        List<Long> ids = content.stream().map(Product::getId).toList();
        var ratings = reviewService.summarizeAll(ids);
        var stockByProductId = stockByProductId(ids);
        return page.map(p -> toResponse(p,
                ratings.get(p.getId()),
                stockByProductId.getOrDefault(p.getId(), 0)));
    }

    /** ONE grouped query: product_id → total stock for the given ids. */
    private Map<Long, Integer> stockByProductId(List<Long> productIds) {
        if (productIds.isEmpty()) {
            return Map.of();
        }
        return inventoryRepository.findByProductIdIn(productIds).stream()
                .collect(java.util.stream.Collectors.toMap(
                        inv -> inv.getProduct().getId(),
                        Inventory::getStockQuantity));
    }

    /** Single-product stock lookup used by the detail endpoints. */
    private int stockOf(Product product) {
        return product.getInventory() != null
                ? product.getInventory().getStockQuantity() : 0;
    }

    private ProductResponseDTO toResponse(Product product,
                    com.shopsphere.dto.RatingSummaryDTO rating, int stock) {
        String status = product.isActive() ? "ACTIVE" : "INACTIVE";
        return new ProductResponseDTO(
                product.getId(),
                product.getName(),
                product.getSku(),
                product.getPrice(),
                stock,
                product.getCategory().getId(),
                status,
                rating
        );
    }
}
