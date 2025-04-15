package com.sondv.phone.repository;

import com.sondv.phone.model.Discount;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.OffsetDateTime;
import java.util.Optional;

public interface DiscountRepository extends JpaRepository<Discount, Long> {
    Optional<Discount> findByCode(String code);

    @Query("""
    SELECT d FROM Discount d
    WHERE d.validFrom <= :now
      AND d.validTo >= :now
      AND d.probabilityWeight > 0
      AND (:minPercentage IS NULL OR d.discountPercentage >= :minPercentage)
""")
    Page<Discount> findActiveDiscountsWithMinPercentage(
            @Param("now") OffsetDateTime now,
            @Param("minPercentage") Double minPercentage,
            Pageable pageable
    );

}
