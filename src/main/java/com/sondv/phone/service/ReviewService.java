package com.sondv.phone.service;

import com.sondv.phone.dto.ReviewResponse;
import com.sondv.phone.entity.*;
import com.sondv.phone.repository.*;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

@Service
@RequiredArgsConstructor
public class ReviewService {

    private final ReviewRepository reviewRepository;
    private final OrderDetailRepository orderDetailRepository;
    private final ProductRepository productRepository;

    @Transactional
    public Review addReview(Review review) {
        // 1. Kiểm tra OrderDetail tồn tại và hợp lệ
        OrderDetail orderDetail = review.getOrderDetail();
        if (orderDetail == null || orderDetail.getProduct() == null) {
            throw new IllegalArgumentException("Review must be associated with a valid OrderDetail and Product.");
        }

        // 2. Dự phòng - kiểm tra sản phẩm đã được đánh giá chưa
        if (orderDetail.getReview() != null) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Sản phẩm trong đơn hàng này đã được đánh giá");
        }

        // 3. Lưu review mới
        Review savedReview = reviewRepository.save(review);

        // 4. Cập nhật lại rating trung bình cho sản phẩm
        updateProductRating(orderDetail.getProduct());

        // 5. Trả về review đã lưu
        return savedReview;
    }

    @Autowired
    private RedisTemplate<String, String> redisTemplate;

    private void updateProductRating(Product product) {
        String cacheKey = "product:rating:" + product.getId();

        Double avgRating;
        Long count;

        try {
            String avgRatingStr = redisTemplate.opsForValue().get(cacheKey + ":avg");
            String countStr = redisTemplate.opsForValue().get(cacheKey + ":count");

            avgRating = avgRatingStr != null ? Double.parseDouble(avgRatingStr) : null;
            count = countStr != null ? Long.parseLong(countStr) : null;
        } catch (NumberFormatException e) {
            avgRating = null;
            count = null;
        }

        if (avgRating == null || count == null) {
            avgRating = reviewRepository.findAverageRatingByProductId(product.getId());
            count = reviewRepository.countReviewsByProductId(product.getId());

            redisTemplate.opsForValue().set(cacheKey + ":avg", avgRating != null ? avgRating.toString() : "0.0");
            redisTemplate.opsForValue().set(cacheKey + ":count", count != null ? count.toString() : "0");
        }

        product.setRating(avgRating != null ? avgRating : 0.0);
        product.setRatingCount(count != null ? count.intValue() : 0);
        productRepository.save(product);
    }

    public Page<ReviewResponse> getPagedReviews(Long productId, Pageable pageable) {
        return reviewRepository.findByOrderDetail_Product_Id(productId, pageable)
                .map(review -> {
                    ReviewResponse dto = new ReviewResponse();
                    dto.setId(review.getId());
                    dto.setRating(review.getRating());
                    dto.setComment(review.getComment());
                    dto.setCreatedAt(review.getCreatedAt().toString());
                    dto.setProductName(review.getOrderDetail().getProduct().getName());
                    dto.setCustomerName(review.getOrderDetail().getOrder().getCustomer().getFullName());
                    return dto;
                });
    }

    public Double getAverageRating(Long productId) {
        Double avg = reviewRepository.findAverageRatingByProductId(productId);
        return avg != null ? avg : 0.0;
    }

    public Long getReviewCount(Long productId) {
        return reviewRepository.countReviewsByProductId(productId);
    }

    public List<Review> getReviewsByProduct(Long productId) {
        return reviewRepository.findAllByProductId(productId);
    }
}
