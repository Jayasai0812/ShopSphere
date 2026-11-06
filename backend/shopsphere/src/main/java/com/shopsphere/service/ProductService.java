package com.shopsphere.service;

import com.shopsphere.dto.ProductRequestDTO;
import com.shopsphere.dto.ProductResponseDTO;
import com.shopsphere.dto.ProductUpdateRequestDTO;
import com.shopsphere.entity.Inventory;
import com.shopsphere.entity.Product;
import com.shopsphere.exception.DuplicateSkuException;
import com.shopsphere.exception.ResourceNotFoundException;
import com.shopsphere.repository.CategoryRepository;
import com.shopsphere.repository.ProductRepository;
import com.shopsphere.repository.UserRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@Transactional
public class ProductService {

    private final ProductRepository productRepository;
    private final CategoryRepository categoryRepository;
    private final UserRepository userRepository;
    private final com.shopsphere.service.ReviewService reviewService;

    public ProductService(ProductRepository productRepository,
                          CategoryRepository categoryRepository,
                          UserRepository userRepository,
                          com.shopsphere.service.ReviewService reviewService) {
        this.productRepository = productRepository;
        this.categoryRepository = categoryRepository;
        this.userRepository = userRepository;
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
        return toResponse(saved);
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

        return toResponse(productRepository.save(product));
    }

    public ProductResponseDTO setProductStatus(Long id, boolean active) {
        Product product = productRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Product not found with id: " + id));
        product.setActive(active);
        return toResponse(productRepository.save(product));
    }

    public void deleteProduct(Long id) {
        Product product = productRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Product not found with id: " + id));
        product.setActive(false);
        productRepository.save(product);
    }

    @Transactional(readOnly = true)
    public Page<ProductResponseDTO> getAllProducts(Pageable pageable) {
        Page<Product> page = productRepository.findAll(pageable);
        // ONE grouped query for all rating summaries on the page (no N+1).
        var ratings = reviewService.summarizeAll(
                page.getContent().stream().map(Product::getId).toList());
        return page.map(p -> toResponse(p, ratings.get(p.getId())));
    }

    @Transactional(readOnly = true)
    public ProductResponseDTO getProductById(Long id) {
        return productRepository.findById(id)
                .map(p -> toResponse(p, reviewService.summarize(id)))
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Product not found with id: " + id));
    }

    @Transactional(readOnly = true)
    public List<ProductResponseDTO> getProductsByCategory(Long categoryId) {
        if (!categoryRepository.existsById(categoryId)) {
            throw new ResourceNotFoundException("Category not found with id: " + categoryId);
        }
        return productRepository.findByCategoryId(categoryId)
                .stream().map(p -> toResponse(p, reviewService.summarize(p.getId()))).toList();
    }

    private ProductResponseDTO toResponse(Product product) {
        return toResponse(product, reviewService.summarize(product.getId()));
    }

    private ProductResponseDTO toResponse(Product product,
                    com.shopsphere.dto.RatingSummaryDTO rating) {
        int stock = product.getInventory() != null
                ? product.getInventory().getStockQuantity() : 0;
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
