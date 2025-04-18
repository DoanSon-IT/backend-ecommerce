package com.sondv.phone.repository;

import com.sondv.phone.entity.Product;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface ProductRepository extends JpaRepository<Product, Long> {

    // 🔍 Tìm theo tên sản phẩm
    List<Product> findTop3ByNameContainingIgnoreCase(String keyword);
    List<Product> findTop2ByNameContainingIgnoreCase(String keyword);
    List<Product> findTop5ByNameContainingIgnoreCase(String keyword);
    Optional<Product> findFirstByNameContainingIgnoreCase(String keyword);
    Page<Product> findByNameContainingIgnoreCase(String name, Pageable pageable);

    // 🛒 Tìm theo danh mục
    List<Product> findByCategoryId(Long categoryId);
    List<Product> findByCategoryIdAndSellingPriceLessThan(Long categoryId, BigDecimal price);

    // 🛍️ Top bán chạy
    List<Product> findTop5ByOrderBySoldQuantityDesc();

    List<Product> findTop3ByOrderBySoldQuantityDesc();

    // 📦 Hết hàng / còn ít
    List<Product> findByStockLessThan(int threshold);

    // 💡 Gợi ý nổi bật
    List<Product> findByIsFeaturedTrue();
    List<Product> findAllByOrderByIdDesc();

    // 🔻 Giảm giá đã hết hạn
    List<Product> findByDiscountEndDateBefore(LocalDateTime dateTime);

    // 📊 Bán chạy theo số lượng
    List<Product> findBySoldQuantityGreaterThan(int quantity);

    // 🧠 So sánh / lọc mở rộng
    List<Product> findByNameInIgnoreCase(List<String> names); // So sánh 2 sản phẩm
    List<Product> findBySellingPriceLessThan(BigDecimal maxPrice); // Sản phẩm giá rẻ
    List<Product> findBySellingPriceGreaterThan(BigDecimal minPrice); // Sản phẩm cao cấp
    List<Product> findByNameContainingIgnoreCaseAndSellingPriceLessThan(String keyword, BigDecimal maxPrice);
    List<Product> findByNameContainingIgnoreCaseAndSellingPriceGreaterThan(String keyword, BigDecimal minPrice);

    // 🖼️ Tải kèm ảnh, danh mục, nhà cung cấp
    @EntityGraph(attributePaths = {"images", "category", "supplier"})
    @Query("SELECT p FROM Product p")
    List<Product> findAllWithCategoryAndSupplier();

    // 🔢 Tuỳ biến lấy top N
    @Query("SELECT p FROM Product p ORDER BY p.soldQuantity DESC")
    List<Product> findTopNByOrderBySoldQuantityDesc(org.springframework.data.domain.Pageable pageable);
}
