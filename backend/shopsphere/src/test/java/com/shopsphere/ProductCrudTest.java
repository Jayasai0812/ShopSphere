package com.shopsphere;

import com.shopsphere.dto.ProductUpdateRequestDTO;
import com.shopsphere.entity.Category;
import com.shopsphere.entity.Inventory;
import com.shopsphere.entity.Product;
import com.shopsphere.entity.User;
import com.shopsphere.exception.DuplicateSkuException;
import com.shopsphere.exception.ResourceNotFoundException;
import com.shopsphere.repository.CategoryRepository;
import com.shopsphere.repository.ProductRepository;
import com.shopsphere.repository.UserRepository;
import com.shopsphere.service.ProductService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@Transactional
class ProductCrudTest {

    @Autowired private ProductService productService;
    @Autowired private ProductRepository productRepository;
    @Autowired private CategoryRepository categoryRepository;
    @Autowired private UserRepository userRepository;

    private Long productId;
    private Long categoryId;

    @BeforeEach
    void setUp() {
        User seller = new User();
        seller.setName("Seller");
        seller.setEmail("seller-crud@shopsphere.test");
        seller.setPasswordHash("$2a$10$dXJ3SW6G7P50lGmMkkmwe.20cQQubK3.HZWzG3YB1tlRy.fqvM/BG");
        userRepository.save(seller);

        Category category = new Category();
        category.setName("Test Category");
        categoryRepository.save(category);
        categoryId = category.getId();

        Product product = new Product();
        product.setName("Original Name");
        product.setSku("SKU-ORIG");
        product.setPrice(new BigDecimal("999.00"));
        product.setCategory(category);
        product.setSeller(seller);
        product.setActive(true);

        Inventory inventory = new Inventory();
        inventory.setProduct(product);
        inventory.setStockQuantity(10);
        product.setInventory(inventory);

        productRepository.save(product);
        productId = product.getId();
    }

    @Test
    void updatesProductSuccessfully() {
        var request = new ProductUpdateRequestDTO(
                "Updated Name", "New desc", "SKU-ORIG", new BigDecimal("1299.00"), 20, categoryId);

        var response = productService.updateProduct(productId, request);

        assertThat(response.name()).isEqualTo("Updated Name");
        assertThat(response.price()).isEqualByComparingTo("1299.00");
        assertThat(response.stockQuantity()).isEqualTo(20);
    }

    @Test
    void updateReturns404ForMissingProduct() {
        var request = new ProductUpdateRequestDTO(
                "Name", null, "SKU-X", new BigDecimal("100.00"), 5, categoryId);

        assertThatThrownBy(() -> productService.updateProduct(999L, request))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("999");
    }

    @Test
    void updateRejects404ForMissingCategory() {
        var request = new ProductUpdateRequestDTO(
                "Name", null, "SKU-ORIG", new BigDecimal("100.00"), 5, 999L);

        assertThatThrownBy(() -> productService.updateProduct(productId, request))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("Category");
    }

    @Test
    void updateRejectsDuplicateSku() {
        // create a second product with a different SKU
        Product other = new Product();
        other.setName("Other");
        other.setSku("SKU-OTHER");
        other.setPrice(new BigDecimal("500.00"));
        other.setCategory(categoryRepository.findById(categoryId).orElseThrow());
        other.setSeller(userRepository.findByEmail("seller-crud@shopsphere.test").orElseThrow());
        other.setActive(true);
        productRepository.save(other);

        // try to update first product with second product's SKU
        var request = new ProductUpdateRequestDTO(
                "Name", null, "SKU-OTHER", new BigDecimal("100.00"), 5, categoryId);

        assertThatThrownBy(() -> productService.updateProduct(productId, request))
                .isInstanceOf(DuplicateSkuException.class);
    }

    @Test
    void deactivatesAndActivatesProduct() {
        var inactive = productService.setProductStatus(productId, false);
        assertThat(inactive.status()).isEqualTo("INACTIVE");

        var active = productService.setProductStatus(productId, true);
        assertThat(active.status()).isEqualTo("ACTIVE");
    }

    @Test
    void setStatusReturns404ForMissingProduct() {
        assertThatThrownBy(() -> productService.setProductStatus(999L, false))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void deletesProductSuccessfully() {
        productService.deleteProduct(productId);
        var found = productRepository.findById(productId).orElseThrow();
        assertThat(found.isActive()).isFalse();
    }

    @Test
    void deleteReturns404ForMissingProduct() {
        assertThatThrownBy(() -> productService.deleteProduct(999L))
                .isInstanceOf(ResourceNotFoundException.class);
    }
}
