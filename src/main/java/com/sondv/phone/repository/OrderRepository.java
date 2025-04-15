package com.sondv.phone.repository;

import com.sondv.phone.model.Order;
import com.sondv.phone.model.OrderStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface OrderRepository extends JpaRepository<Order, Long>, JpaSpecificationExecutor<Order> {

    List<Order> findByCustomerId(Long customerId);

    long countByStatus(OrderStatus status);

    List<Order> findByCreatedAtBetweenAndStatus(LocalDateTime startDate, LocalDateTime endDate, OrderStatus status);

    List<Order> findTop10ByOrderByCreatedAtDesc();

    @Query("SELECT SUM(o.totalPrice) FROM Order o")
    Double sumTotalRevenue();

    long countByStatusAndCreatedAtBetween(OrderStatus status, LocalDateTime startDate, LocalDateTime endDate);

    @Query("SELECT o FROM Order o JOIN o.shippingInfo s WHERE s.trackingNumber = :trackingNumber")
    Optional<Order> findByTrackingNumber(String trackingNumber);

    @Query("SELECT SUM(o.totalPrice) FROM Order o WHERE o.createdAt BETWEEN :startDate AND :endDate AND o.status = 'COMPLETED'")
    Double sumTotalRevenueByDateRange(LocalDateTime startDate, LocalDateTime endDate);

    @Query("SELECT FUNCTION('DATE', o.createdAt), SUM(o.totalPrice) " +
            "FROM Order o WHERE o.createdAt BETWEEN :start AND :end " +
            "GROUP BY FUNCTION('DATE', o.createdAt) " +
            "ORDER BY FUNCTION('DATE', o.createdAt)")
    List<Object[]> getRevenueGroupedByDate(LocalDateTime start, LocalDateTime end);

    @Query("SELECT o FROM Order o JOIN o.shippingInfo s WHERE s.carrier = :carrier")
    List<Order> findByCarrier(String carrier);

    List<Order> findByCreatedAtAfter(LocalDateTime startDate);

    @Query("SELECT o FROM Order o ORDER BY o.createdAt DESC LIMIT ?1")
    List<Order> findTopNByOrderByCreatedAtDesc(int limit);

    List<Order> findByCreatedAtBetween(LocalDateTime start, LocalDateTime end);

    @Query(value = """
    SELECT
        CASE
            WHEN :type = 'day' THEN FUNCTION('DATE', o.createdAt)
            WHEN :type = 'month' THEN FUNCTION('DATE_FORMAT', o.createdAt, '%Y-%m')
            WHEN :type = 'year' THEN FUNCTION('YEAR', o.createdAt)
        END as period,
        SUM((od.price - p.costPrice) * od.quantity) AS totalProfit,
        SUM(od.price * od.quantity) AS totalRevenue
    FROM Order o
    JOIN o.orderDetails od
    JOIN od.product p
    WHERE o.createdAt BETWEEN :start AND :end AND o.status = 'COMPLETED'
    GROUP BY period
    ORDER BY period
    """)
    List<Object[]> getProfitGroupedBy(@Param("type") String type,
                                      @Param("start") LocalDateTime start,
                                      @Param("end") LocalDateTime end);
}
