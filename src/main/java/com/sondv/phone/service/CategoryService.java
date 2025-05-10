package com.sondv.phone.service;

import com.sondv.phone.entity.Category;
import com.sondv.phone.entity.Product;
import com.sondv.phone.repository.CategoryRepository;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class CategoryService {

    private final CategoryRepository categoryRepository;

    // Lấy danh sách danh mục
    @Cacheable(value = "categories")
    public List<Category> getAllCategories() {
        return categoryRepository.findAll();
    }

    public List<Product> getProductsByCategoryId(Long categoryId) {
        Optional<Category> categoryOpt = categoryRepository.findById(categoryId);
        if (categoryOpt.isEmpty()) {
            return Collections.emptyList(); // Trả về danh sách rỗng nếu không tìm thấy danh mục
        }
        Category category = categoryOpt.get();
        return new ArrayList<>(category.getProducts());
    }

    // Thêm danh mục
    @Transactional
    @CacheEvict(value = "categories", allEntries = true)
    public String createCategory(String name) {
        Optional<Category> existingCategory = categoryRepository.findByName(name);
        if (existingCategory.isPresent()) {
            return "Danh mục đã tồn tại!";
        }

        Category category = new Category();
        category.setName(name);
        categoryRepository.save(category);

        return "Thêm danh mục thành công!";
    }

    // Cập nhật danh mục
    @Transactional
    @CacheEvict(value = "categories", allEntries = true)
    public String updateCategory(Long id, String name) {
        Category category = categoryRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Danh mục không tồn tại!"));

        category.setName(name);
        categoryRepository.save(category);

        return "Cập nhật danh mục thành công!";
    }

    // Xóa danh mục (Chỉ xóa nếu không có sản phẩm nào)
    @Transactional
    @CacheEvict(value = "categories", allEntries = true)
    public String deleteCategory(Long id) {
        Category category = categoryRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Danh mục không tồn tại!"));

        if (category.getProducts() != null && !category.getProducts().isEmpty()) {
            return "Không thể xóa danh mục vì đang chứa sản phẩm!";
        }

        categoryRepository.delete(category);
        return "Xóa danh mục thành công!";
    }
}
