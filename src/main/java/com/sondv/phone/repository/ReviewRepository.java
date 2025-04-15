package com.sondv.phone.repository;

import com.sondv.phone.model.Review;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ReviewRepository extends JpaRepository<Review, Long> {
    Page<Review> findByOrderDetail_Product_Id(Long productId, Pageable pageable);

    @Query("SELECT AVG(r.rating) FROM Review r WHERE r.orderDetail.product.id = :productId")
    Double findAverageRatingByProductId(@Param("productId") Long productId);

    Long countByOrderDetail_Product_Id(Long productId);

}
