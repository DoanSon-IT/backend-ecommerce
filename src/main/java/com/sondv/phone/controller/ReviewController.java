package com.sondv.phone.controller;

import com.sondv.phone.dto.ReviewRequest;
import com.sondv.phone.dto.ReviewResponse;
import com.sondv.phone.entity.OrderDetail;
import com.sondv.phone.entity.OrderStatus;
import com.sondv.phone.entity.Review;
import com.sondv.phone.repository.OrderDetailRepository;
import com.sondv.phone.service.ReviewService;
import com.sondv.phone.util.SecurityUtils;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;

@RestController
@RequestMapping("/api/reviews")
@RequiredArgsConstructor
public class ReviewController {

    private final ReviewService reviewService;
    private final OrderDetailRepository orderDetailRepository;

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ReviewResponse addReview(@RequestBody @Valid ReviewRequest dto) {
        Long userId = SecurityUtils.getCurrentUserId();

        // 1. Tìm orderDetail
        OrderDetail orderDetail = orderDetailRepository.findById(dto.getOrderDetailId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Không tìm thấy mục đơn hàng"));

        // 2. Check quyền người dùng
        if (!orderDetail.getOrder().getCustomer().getUser().getId().equals(userId)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Không có quyền đánh giá đơn hàng này");
        }

        // 3. Đảm bảo đơn hàng đã hoàn tất
        if (orderDetail.getOrder().getStatus() != OrderStatus.COMPLETED) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Chỉ được đánh giá sau khi đơn hàng đã hoàn tất");
        }

        // 4. Check duplicate review
        if (orderDetail.getReview() != null) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Sản phẩm trong đơn hàng này đã được đánh giá");
        }

        // 5. Tạo review và gọi service để xử lý lưu
        Review review = new Review();
        review.setRating(dto.getRating());
        review.setComment(dto.getComment());
        review.setCreatedAt(LocalDateTime.now());
        review.setOrderDetail(orderDetail);

        Review saved = reviewService.addReview(review);

        // 6. Trả về DTO response
        ReviewResponse response = new ReviewResponse();
        response.setId(saved.getId());
        response.setRating(saved.getRating());
        response.setComment(saved.getComment());
        response.setCreatedAt(saved.getCreatedAt().toString());
        response.setProductName(orderDetail.getProduct().getName());
        response.setCustomerName(orderDetail.getOrder().getCustomer().getFullName());

        return response;
    }

    @GetMapping("/product/{productId}")
    public Page<ReviewResponse> getPagedReviews(
            @PathVariable Long productId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "5") int size,
            @RequestParam(defaultValue = "createdAt") String sortBy,
            @RequestParam(defaultValue = "desc") String direction
    ) {
        Pageable pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.fromString(direction), sortBy));
        return reviewService.getPagedReviews(productId, pageable);
    }

    @GetMapping("/product/{productId}/average")
    public Double getAverageRating(@PathVariable Long productId) {
        return reviewService.getAverageRating(productId);
    }

    @GetMapping("/product/{productId}/count")
    public Long getReviewCount(@PathVariable Long productId) {
        return reviewService.getReviewCount(productId);
    }
}
