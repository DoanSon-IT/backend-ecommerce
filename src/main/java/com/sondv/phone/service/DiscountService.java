package com.sondv.phone.service;

import com.sondv.phone.dto.DiscountApplyRequest;
import com.sondv.phone.dto.DiscountApplyResponse;
import com.sondv.phone.model.Discount;
import com.sondv.phone.model.Product;
import com.sondv.phone.repository.DiscountRepository;
import com.sondv.phone.repository.ProductRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class DiscountService {

    private final DiscountRepository discountRepository;
    private final ProductRepository productRepository;

    public Discount createDiscount(Discount discount) {
        return discountRepository.save(discount);
    }

    public Page<Discount> getAllDiscounts(Pageable pageable) {
        return discountRepository.findAll(pageable);
    }

    public List<Discount> getAll() {
        return discountRepository.findAll();
    }

    public Page<Discount> getActiveDiscounts(Double minPercentage, Pageable pageable) {
        OffsetDateTime now = OffsetDateTime.now();
        return discountRepository.findActiveDiscountsWithMinPercentage(now, minPercentage, pageable);
    }

    public Optional<Discount> getDiscountByCode(String code) {
        return discountRepository.findByCode(code);
    }

    public Discount updateDiscount(Long id, Discount updatedDiscount) {
        Discount existing = discountRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Discount not found"));

        existing.setCode(updatedDiscount.getCode());
        existing.setDiscountPercentage(updatedDiscount.getDiscountPercentage());
        existing.setValidFrom(updatedDiscount.getValidFrom());
        existing.setValidTo(updatedDiscount.getValidTo());
        existing.setMinOrderValue(updatedDiscount.getMinOrderValue());
        existing.setProbabilityWeight(updatedDiscount.getProbabilityWeight());

        return discountRepository.save(existing);
    }

    public void deleteDiscount(Long id) {
        discountRepository.deleteById(id);
    }

    public DiscountApplyResponse applyDiscountLogic(DiscountApplyRequest request) {
        System.out.println("🧾 Mã giảm giá nhận được: " + request.getDiscountCode());

        if (request.getItems() == null || request.getItems().isEmpty()) {
            throw new IllegalArgumentException("Vui lòng chọn ít nhất một sản phẩm.");
        }

        Discount discount = discountRepository.findByCode(request.getDiscountCode())
                .orElseThrow(() -> new IllegalArgumentException("Mã giảm giá không tồn tại."));

        OffsetDateTime now = OffsetDateTime.now();

        if (discount.getValidTo().isBefore(now)) {
            throw new IllegalArgumentException("Mã giảm giá đã hết hạn.");
        }

        BigDecimal originalTotal = BigDecimal.ZERO;

        for (DiscountApplyRequest.OrderItemRequest item : request.getItems()) {
            Product product = productRepository.findById(item.getProductId())
                    .orElseThrow(() -> new IllegalArgumentException("Sản phẩm không tồn tại hoặc đã bị xoá."));

            // ❌ Không áp mã nếu sản phẩm đang được khuyến mãi
            if (product.getDiscountedPrice() != null &&
                    product.getDiscountStartDate() != null &&
                    product.getDiscountEndDate() != null) {

                // Chuyển LocalDateTime -> OffsetDateTime để so sánh
                OffsetDateTime start = product.getDiscountStartDate().atOffset(ZoneOffset.UTC);
                OffsetDateTime end = product.getDiscountEndDate().atOffset(ZoneOffset.UTC);

                boolean isDiscountActive = !now.isBefore(start) && !now.isAfter(end);

                if (isDiscountActive) {
                    throw new IllegalArgumentException(
                            "Sản phẩm \"" + product.getName() + "\" đang khuyến mãi. Không thể áp thêm mã giảm giá."
                    );
                }
            }

            BigDecimal price = product.getSellingPrice(); // dùng giá gốc để tính điều kiện
            originalTotal = originalTotal.add(price.multiply(BigDecimal.valueOf(item.getQuantity())));
        }

        if (originalTotal.compareTo(BigDecimal.valueOf(discount.getMinOrderValue())) < 0) {
            throw new IllegalArgumentException("Đơn hàng chưa đạt giá trị tối thiểu để sử dụng mã.");
        }

        BigDecimal discountAmount = originalTotal
                .multiply(BigDecimal.valueOf(discount.getDiscountPercentage()))
                .divide(BigDecimal.valueOf(100));

        BigDecimal finalTotal = originalTotal.subtract(discountAmount);

        return new DiscountApplyResponse(originalTotal, discountAmount, finalTotal, "Mã giảm giá đã được áp dụng.");
    }

    private BigDecimal resolveCurrentPrice(Product product) {
        OffsetDateTime now = OffsetDateTime.now();

        boolean inDiscountPeriod = product.getDiscountedPrice() != null
                && product.getDiscountStartDate() != null
                && product.getDiscountEndDate() != null
                && now.isAfter(product.getDiscountStartDate().atOffset(now.getOffset()))
                && now.isBefore(product.getDiscountEndDate().atOffset(now.getOffset()));

        return inDiscountPeriod ? product.getDiscountedPrice() : product.getSellingPrice();
    }

}