package com.sondv.phone.service;

import com.sondv.phone.dto.ReviewRequestDto;
import com.sondv.phone.dto.ReviewResponseDto;
import com.sondv.phone.model.*;
import com.sondv.phone.repository.*;
import com.sondv.phone.util.SecurityUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
public class ReviewService {
    private final ReviewRepository reviewRepository;
    private final OrderDetailRepository orderDetailRepository;

    public ReviewResponseDto addReview(ReviewRequestDto dto, Long customerId) {


        OrderDetail orderDetail = orderDetailRepository.findById(dto.getOrderDetailId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Không tìm thấy mục đơn hàng"));

        Order order = orderDetail.getOrder();

        System.out.println("🔥 DEBUG: customerId từ JWT: " + customerId);
        System.out.println("🔥 DEBUG: customerId của order: " + order.getCustomer().getId());

        Long userId = SecurityUtils.getCurrentUserId();
        if (!order.getCustomer().getUser().getId().equals(userId)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Không có quyền đánh giá đơn hàng này");
        }

        if (order.getStatus() != OrderStatus.COMPLETED) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Chỉ được đánh giá sau khi đơn hàng đã hoàn tất");
        }

        if (orderDetail.getReview() != null) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Sản phẩm trong đơn hàng này đã được đánh giá");
        }

        // ✅ Tạo và lưu Review
        Review review = new Review();
        review.setOrderDetail(orderDetail);
        review.setRating(dto.getRating());
        review.setComment(dto.getComment());
        review.setCreatedAt(LocalDateTime.now());
        reviewRepository.save(review);

        Product product = orderDetail.getProduct();
        Double avgRating = reviewRepository.findAverageRatingByProductId(product.getId());
        Long count = reviewRepository.countByOrderDetail_Product_Id(product.getId());

        product.setRating(avgRating);
        product.setRatingCount(count.intValue());

        ReviewResponseDto response = new ReviewResponseDto();
        response.setId(review.getId());
        response.setRating(review.getRating());
        response.setComment(review.getComment());
        response.setCreatedAt(review.getCreatedAt().toString());
        response.setProductName(product.getName());
        response.setCustomerName(order.getCustomer().getFullName());

        return response;
    }

    public Page<ReviewResponseDto> getPagedReviews(Long productId, Pageable pageable) {
        return reviewRepository.findByOrderDetail_Product_Id(productId, pageable)
                .map(review -> {
                    ReviewResponseDto dto = new ReviewResponseDto();
                    dto.setId(review.getId());
                    dto.setRating(review.getRating());
                    dto.setComment(review.getComment());
                    dto.setCreatedAt(review.getCreatedAt().toString());
                    dto.setProductName(review.getOrderDetail().getProduct().getName());
                    dto.setCustomerName(review.getOrderDetail().getOrder().getCustomer().getFullName());
                    return dto;
                });
    }

}
