package com.sondv.phone.controller;

import com.sondv.phone.dto.ReviewRequestDto;
import com.sondv.phone.dto.ReviewResponseDto;
import com.sondv.phone.service.ReviewService;
import com.sondv.phone.util.SecurityUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/reviews")
@RequiredArgsConstructor
public class ReviewController {

    private final ReviewService reviewService;

    @PostMapping
    public ResponseEntity<ReviewResponseDto> addReview(@RequestBody ReviewRequestDto dto) {
        Long customerId = SecurityUtils.getCurrentUserId();
        ReviewResponseDto response = reviewService.addReview(dto, customerId);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/product/{productId}/paged")
    public ResponseEntity<?> getPagedReviews(@PathVariable Long productId,
                                             @RequestParam(defaultValue = "0") int page,
                                             @RequestParam(defaultValue = "5") int size,
                                             @RequestParam(defaultValue = "createdAt") String sortBy,
                                             @RequestParam(defaultValue = "desc") String direction) {
        Sort sort = direction.equalsIgnoreCase("asc") ? Sort.by(sortBy).ascending() : Sort.by(sortBy).descending();
        Pageable pageable = PageRequest.of(page, size, sort);

        return ResponseEntity.ok(reviewService.getPagedReviews(productId, pageable));
    }
}
