package com.shopsphere;

import com.shopsphere.dto.CategoryRequestDTO;
import com.shopsphere.entity.Category;
import com.shopsphere.entity.Inventory;
import com.shopsphere.entity.Product;
import com.shopsphere.entity.User;
import com.shopsphere.exception.ResourceNotFoundException;
import com.shopsphere.repository.CategoryRepository;
import com.shopsphere.repository.ProductRepository;
import com.shopsphere.repository.UserRepository;
import com.shopsphere.service.CategoryService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@Transactional
class CategoryServiceTest {

    @Autowired private CategoryService categoryService;
    @Autowired private CategoryRepository categoryRepository;
    @Autowired private ProductRepository productRepository;
    @Autowired private UserRepository userRepository;

    @Test
    void createsCategorySuccessfully() {
        var response = categoryService.createCategory(
                new CategoryRequestDTO("Gadgets", "All gadgets"));

        assertThat(response.id()).isNotNull();
        assertThat(response.name()).isEqualTo("Gadgets");
        assertThat(response.description()).isEqualTo("All gadgets");
        assertThat(response.active()).isTrue();
    }

    @Test
    void rejectsDuplicateCategoryName() {
        categoryService.createCategory(new CategoryRequestDTO("Books", null));

        assertThatThrownBy(() ->
                categoryService.createCategory(new CategoryRequestDTO("Books", null)))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("already exists");
    }

    @Test
    void rejectsBlankCategoryName() {
        assertThatThrownBy(() ->
                categoryService.createCategory(new CategoryRequestDTO("", null)))
                .isInstanceOf(Exception.class);
    }

    @Test
    void getAllCategoriesReturnsList() {
        categoryService.createCategory(new CategoryRequestDTO("Cat A", null));
        categoryService.createCategory(new CategoryRequestDTO("Cat B", null));

        var all = categoryService.getAllCategories();
        assertThat(all).hasSizeGreaterThanOrEqualTo(2);
    }

    @Test
    void getCategoryByIdReturns404WhenMissing() {
        assertThatThrownBy(() -> categoryService.getCategoryById(999L))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void updatesCategorySuccessfully() {
        var created = categoryService.createCategory(
                new CategoryRequestDTO("Old Name", null));

        var updated = categoryService.updateCategory(created.id(),
                new CategoryRequestDTO("New Name", "Updated desc"));

        assertThat(updated.name()).isEqualTo("New Name");
        assertThat(updated.description()).isEqualTo("Updated desc");
    }

    @Test
    void updateRejectsDuplicateNameOnDifferentCategory() {
        categoryService.createCategory(new CategoryRequestDTO("Taken", null));
        var second = categoryService.createCategory(new CategoryRequestDTO("Second", null));

        assertThatThrownBy(() ->
                categoryService.updateCategory(second.id(),
                        new CategoryRequestDTO("Taken", null)))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("already exists");
    }

    @Test
    void updateReturns404ForMissingCategory() {
        assertThatThrownBy(() ->
                categoryService.updateCategory(999L,
                        new CategoryRequestDTO("Name", null)))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void softDeletesCategoryWithNoProducts() {
        var created = categoryService.createCategory(
                new CategoryRequestDTO("Empty Cat", null));

        categoryService.deleteCategory(created.id());

        var found = categoryRepository.findById(created.id()).orElseThrow();
        assertThat(found.isActive()).isFalse();
    }

    @Test
    void deleteRejectsWhenCategoryHasProducts() {
        var created = categoryService.createCategory(
                new CategoryRequestDTO("Has Products", null));

        User seller = new User();
        seller.setName("Seller");
        seller.setEmail("cat-seller@shopsphere.test");
        seller.setPasswordHash("$2a$10$dXJ3SW6G7P50lGmMkkmwe.20cQQubK3.HZWzG3YB1tlRy.fqvM/BG");
        userRepository.save(seller);

        Category category = categoryRepository.findById(created.id()).orElseThrow();
        Product product = new Product();
        product.setName("Test Product");
        product.setSku("CAT-SKU-001");
        product.setPrice(new BigDecimal("100.00"));
        product.setCategory(category);
        product.setSeller(seller);
        product.setActive(true);
        Inventory inv = new Inventory();
        inv.setProduct(product);
        inv.setStockQuantity(5);
        product.setInventory(inv);
        productRepository.save(product);

        assertThatThrownBy(() -> categoryService.deleteCategory(created.id()))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("associated products");
    }

    @Test
    void deleteReturns404ForMissingCategory() {
        assertThatThrownBy(() -> categoryService.deleteCategory(999L))
                .isInstanceOf(ResourceNotFoundException.class);
    }
}
