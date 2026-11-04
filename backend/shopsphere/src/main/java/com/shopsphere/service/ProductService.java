package com.shopsphere.service;

import com.shopsphere.dto.ProductRequestDTO;
import com.shopsphere.dto.ProductResponseDTO;
import com.shopsphere.entity.Inventory;
import com.shopsphere.entity.Product;
import com.shopsphere.exception.ResourceNotFoundException;
import com.shopsphere.repository.CategoryRepository;
import com.shopsphere.repository.ProductRepository;
import com.shopsphere.repository.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class ProductService {

    private final ProductRepository productRepository;
    private final CategoryRepository categoryRepository;
    private final UserRepository userRepository;

    public ProductService(ProductRepository productRepository,
                          CategoryRepository categoryRepository,
                          UserRepository userRepository) {
        this.productRepository = productRepository;
        this.categoryRepository = categoryRepository;
        this.userRepository = userRepository;
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

        Inventory inventory = new Inventory();
        inventory.setProduct(product);
        inventory.setStockQuantity(request.stockQuantity());
        product.setInventory(inventory);

        Product saved = productRepository.save(product);

        return toResponse(saved);
    }

    private ProductResponseDTO toResponse(Product product) {
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
                status
        );
    }
}
