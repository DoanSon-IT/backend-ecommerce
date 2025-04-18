package com.sondv.phone.repository;

import com.sondv.phone.dto.InventoryReportDTO;
import com.sondv.phone.entity.Inventory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface InventoryRepository extends JpaRepository<Inventory, Long> {
    Optional<Inventory> findByProductId(Long productId);

    // Thêm phương thức kiểm tra tồn tại Inventory theo productId
    boolean existsByProductId(Long productId);

    // Thêm phương thức xóa Inventory theo productId
    @Modifying
    @Query("DELETE FROM Inventory i WHERE i.product.id = :productId")
    void deleteByProductId(@Param("productId") Long productId);

    @Query("SELECT i FROM Inventory i WHERE i.product.name LIKE %:name%")
    Page<Inventory> findByProductNameContaining(@Param("name") String name, Pageable pageable);

    @Query("SELECT new com.sondv.phone.dto.InventoryReportDTO(" +
            "i.product.id, i.product.name, i.product.category.name, i.quantity, i.lastUpdated, " +
            "CASE WHEN i.quantity = 0 THEN 'OUT_OF_STOCK' " +
            "WHEN i.quantity < i.minQuantity THEN 'LOW_STOCK' ELSE 'IN_STOCK' END) " +
            "FROM Inventory i " +
            "WHERE (:name IS NULL OR i.product.name LIKE %:name%) " +
            "AND (:status IS NULL OR " +
            "(CASE WHEN i.quantity = 0 THEN 'OUT_OF_STOCK' " +
            "WHEN i.quantity < i.minQuantity THEN 'LOW_STOCK' ELSE 'IN_STOCK' END) = :status)")
    Page<InventoryReportDTO> findInventoryReport(
            @Param("name") String name,
            @Param("status") String status,
            Pageable pageable);
}