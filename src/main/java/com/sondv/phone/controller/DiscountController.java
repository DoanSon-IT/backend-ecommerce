package com.sondv.phone.controller;

import com.sondv.phone.dto.DiscountApplyRequest;
import com.sondv.phone.dto.DiscountApplyResponse;
import com.sondv.phone.entity.Discount;
import com.sondv.phone.dto.DiscountDTO;
import com.sondv.phone.service.DiscountService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.*;

@RestController
@RequestMapping("/api/discounts")
@RequiredArgsConstructor
public class DiscountController {
    private final DiscountService discountService;

    @PostMapping
    public ResponseEntity<Discount> createDiscount(@RequestBody DiscountDTO discountDTO) {
        Discount discount = new Discount();
        discount.setCode(discountDTO.getCode());
        discount.setDiscountPercentage(discountDTO.getDiscountPercentage());
        discount.setValidFrom(discountDTO.getValidFrom());
        discount.setValidTo(discountDTO.getValidTo());
        discount.setMinOrderValue(discountDTO.getMinOrderValue());
        discount.setProbabilityWeight(discountDTO.getProbabilityWeight());

        Discount createdDiscount = discountService.createDiscount(discount);
        return ResponseEntity.ok(createdDiscount);
    }

    @GetMapping
    public ResponseEntity<Page<Discount>> getAllDiscounts(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size
    ) {
        Pageable pageable = PageRequest.of(page, size, Sort.by("validTo").descending());
        Page<Discount> discounts = discountService.getAllDiscounts(pageable);
        return ResponseEntity.ok(discounts);
    }

    @PostMapping("/apply-discount")
    public ResponseEntity<?> applyDiscount(@RequestBody DiscountApplyRequest request) {
        try {
            DiscountApplyResponse response = discountService.applyDiscountLogic(request);
            return ResponseEntity.ok(response);
        } catch (IllegalArgumentException e) {
            Map<String, String> error = new HashMap<>();
            error.put("message", e.getMessage());
            return ResponseEntity.badRequest().body(error);
        }
    }

    @GetMapping("/spin")
    public ResponseEntity<Discount> spinDiscount() {
        OffsetDateTime now = OffsetDateTime.now(ZoneId.of("Asia/Ho_Chi_Minh"));
        List<Discount> candidates = discountService.getAll().stream()
                .filter(d -> !d.getValidFrom().isAfter(now) && !d.getValidTo().isBefore(now))
                .filter(d -> d.getProbabilityWeight() > 0)
                .toList();

        if (candidates.isEmpty()) return ResponseEntity.noContent().build();

        int totalWeight = candidates.stream().mapToInt(Discount::getProbabilityWeight).sum();
        int rand = new Random().nextInt(totalWeight) + 1;

        int cumulative = 0;
        for (Discount d : candidates) {
            cumulative += d.getProbabilityWeight();
            if (rand <= cumulative) {
                return ResponseEntity.ok(d);
            }
        }

        return ResponseEntity.internalServerError().build();
    }

    @GetMapping("/active")
    public ResponseEntity<Page<Discount>> getActiveDiscounts(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(defaultValue = "validTo") String sortBy,
            @RequestParam(defaultValue = "desc") String direction,
            @RequestParam(required = false) Double minPercentage
    ) {
        Sort sort = direction.equalsIgnoreCase("desc") ? Sort.by(sortBy).descending() : Sort.by(sortBy).ascending();
        Pageable pageable = PageRequest.of(page, size, sort);
        Page<Discount> discounts = discountService.getActiveDiscounts(minPercentage, pageable);
        return ResponseEntity.ok(discounts);
    }

    @GetMapping("/{code}")
    public ResponseEntity<Discount> getDiscountByCode(@PathVariable String code) {
        return discountService.getDiscountByCode(code)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @PutMapping("/{id}")
    public ResponseEntity<Discount> updateDiscount(@PathVariable Long id, @RequestBody Discount discountDetails) {
        Discount updated = discountService.updateDiscount(id, discountDetails);
        return ResponseEntity.ok(updated);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<String> deleteDiscount(@PathVariable Long id) {
        discountService.deleteDiscount(id);
        return ResponseEntity.ok("Xóa mã giảm giá thành công!");
    }
}