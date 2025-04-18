package com.sondv.phone.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.OffsetDateTime;

@Entity
@Table(name = "discounts")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class Discount {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(unique = true, nullable = false)
    private String code;

    @Column(nullable = false)
    private Double discountPercentage;

    @Column(nullable = false)
    private OffsetDateTime validFrom;

    @Column(nullable = false)
    private OffsetDateTime validTo;

    @Column(nullable = false)
    private Double minOrderValue;

    @Column(nullable = false)
    private int probabilityWeight;

    @Column(nullable = false)
    private boolean used = false;
}