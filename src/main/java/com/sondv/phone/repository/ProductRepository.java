package com.sondv.phone.repository;

import com.sondv.phone.entity.Product;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface ProductRepository extends JpaRepository<Product, Long> {

    List<Product> findTop3ByNameContainingIgnoreCase(String keyword);

    List<Product> findTop2ByNameContainingIgnoreCase(String keyword);

    List<Product> findTop5ByNameContainingIgnoreCase(String keyword);

    Optional<Product> findFirstByNameContainingIgnoreCase(String keyword);

    Page<Product> findByNameContainingIgnoreCase(String name, Pageable pageable);

    List<Product> findByCategoryId(Long categoryId);

    List<Product> findByCategoryIdAndSellingPriceLessThan(Long categoryId, BigDecimal price);

    List<Product> findTop5ByOrderBySoldQuantityDesc();

    List<Product> findTop3ByOrderBySoldQuantityDesc();

    List<Product> findByStockLessThan(int threshold);

    List<Product> findByIsFeaturedTrue();

    List<Product> findAllByOrderByIdDesc();

    List<Product> findByDiscountEndDateBefore(LocalDateTime dateTime);

    List<Product> findBySoldQuantityGreaterThan(int quantity);

    List<Product> findByNameInIgnoreCase(List<String> names);

    List<Product> findBySellingPriceLessThan(BigDecimal maxPrice);

    List<Product> findBySellingPriceGreaterThan(BigDecimal minPrice);

    List<Product> findByNameContainingIgnoreCaseAndSellingPriceLessThan(String keyword, BigDecimal maxPrice);

    List<Product> findByNameContainingIgnoreCaseAndSellingPriceGreaterThan(String keyword, BigDecimal minPrice);

    @EntityGraph(attributePaths = {"images", "category", "supplier"})
    @Query("SELECT p FROM Product p")
    List<Product> findAllWithCategoryAndSupplier();

    @Query("SELECT p FROM Product p ORDER BY p.soldQuantity DESC")
    List<Product> findTopNByOrderBySoldQuantityDesc(org.springframework.data.domain.Pageable pageable);

    @Query("SELECT p.name FROM Product p")
    List<String> findAllProductNames();

    @Query("SELECT p FROM Product p WHERE LOWER(p.name) LIKE LOWER(CONCAT('%', :keyword, '%'))")
    List<Product> findByNameContainingFlexible(@Param("keyword") String keyword);

    @Query("SELECT p FROM Product p WHERE LOWER(p.name) LIKE LOWER(CONCAT(:brand, '%')) AND p.name LIKE '%' || :model || '%'")
    List<Product> findByBrandAndModel(@Param("brand") String brand, @Param("model") String model);

    @Query("SELECT p FROM Product p WHERE p.id = :productId")
    Optional<Product> findProductById(@Param("productId") Long productId);

    @Query("SELECT p FROM Product p WHERE LOWER(p.name) LIKE LOWER(CONCAT('%', :keyword, '%'))")
    List<Product> findByNameContainingIgnoreCase(@Param("keyword") String keyword);

    @Query("SELECT p FROM Product p WHERE p.category.id = :categoryId AND p.id != :productId AND p.sellingPrice BETWEEN :minPrice AND :maxPrice ORDER BY p.soldQuantity DESC")
    List<Product> findRelatedProducts(@Param("categoryId") Long categoryId, @Param("productId") Long productId, @Param("minPrice") BigDecimal minPrice, @Param("maxPrice") BigDecimal maxPrice, Pageable pageable);
}