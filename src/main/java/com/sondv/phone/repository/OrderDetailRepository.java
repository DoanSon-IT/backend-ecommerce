package com.sondv.phone.repository;

import com.sondv.phone.dto.CategoryRevenueDTO;
import com.sondv.phone.dto.TopProductDTO;
import com.sondv.phone.entity.OrderDetail;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;

public interface OrderDetailRepository extends JpaRepository<OrderDetail, Long> {

    @Query("""
    SELECT new com.sondv.phone.dto.TopProductDTO(
        od.product.id,
        od.product.name,
        SUM(od.quantity)
    )
    FROM OrderDetail od
    WHERE od.order.createdAt BETWEEN :startDate AND :endDate
      AND od.order.status = com.sondv.phone.entity.OrderStatus.COMPLETED
    GROUP BY od.product.id, od.product.name
    ORDER BY SUM(od.quantity) DESC
    """)
    List<TopProductDTO> findTopSellingProducts(
            @Param("startDate") LocalDateTime startDate,
            @Param("endDate") LocalDateTime endDate,
            Pageable pageable
    );

    @Query("""
        SELECT new com.sondv.phone.dto.CategoryRevenueDTO(
            p.category.name,
            SUM(od.price * od.quantity),
            COUNT(DISTINCT o.id),
            COUNT(DISTINCT p.id)
        )
        FROM OrderDetail od
        JOIN od.product p
        JOIN od.order o
        WHERE o.createdAt BETWEEN :start AND :end
        AND o.status = 'COMPLETED'
        GROUP BY p.category.name
    """)
    List<CategoryRevenueDTO> getRevenueByCategory(
            @Param("start") LocalDateTime start,
            @Param("end") LocalDateTime end
    );

}
