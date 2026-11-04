package com.shopsphere.service;

import com.shopsphere.dto.CategoryRequestDTO;
import com.shopsphere.dto.CategoryResponseDTO;
import com.shopsphere.entity.Category;
import com.shopsphere.exception.ResourceNotFoundException;
import com.shopsphere.repository.CategoryRepository;
import com.shopsphere.repository.ProductRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

@Service
@Transactional
public class CategoryService {

    private final CategoryRepository categoryRepository;
    private final ProductRepository productRepository;

    public CategoryService(CategoryRepository categoryRepository,
                           ProductRepository productRepository) {
        this.categoryRepository = categoryRepository;
        this.productRepository = productRepository;
    }

    public CategoryResponseDTO createCategory(CategoryRequestDTO request) {
        String name = request.name().trim();
        if (name.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Category name cannot be blank");
        }
        if (categoryRepository.findByNameIgnoreCase(name).isPresent()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Category name already exists: " + name);
        }
        Category category = new Category();
        category.setName(name);
        category.setDescription(request.description());
        category.setActive(true);
        return toResponse(categoryRepository.save(category));
    }

    @Transactional(readOnly = true)
    public List<CategoryResponseDTO> getAllCategories() {
        return categoryRepository.findAll().stream().map(this::toResponse).toList();
    }

    @Transactional(readOnly = true)
    public CategoryResponseDTO getCategoryById(Long id) {
        return categoryRepository.findById(id)
                .map(this::toResponse)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Category not found with id: " + id));
    }

    public CategoryResponseDTO updateCategory(Long id, CategoryRequestDTO request) {
        Category category = categoryRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Category not found with id: " + id));

        String name = request.name().trim();
        if (categoryRepository.existsByNameIgnoreCaseAndIdNot(name, id)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Category name already exists: " + name);
        }

        category.setName(name);
        category.setDescription(request.description());
        return toResponse(categoryRepository.save(category));
    }

    public void deleteCategory(Long id) {
        Category category = categoryRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Category not found with id: " + id));

        if (!productRepository.findByCategoryId(id).isEmpty()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Cannot delete category with associated products");
        }

        category.setActive(false);
        categoryRepository.save(category);
    }

    private CategoryResponseDTO toResponse(Category category) {
        return new CategoryResponseDTO(
                category.getId(),
                category.getName(),
                category.getDescription(),
                category.isActive()
        );
    }
}
