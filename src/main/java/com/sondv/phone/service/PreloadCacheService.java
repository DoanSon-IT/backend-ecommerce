package com.sondv.phone.service;

import com.sondv.phone.repository.ProductRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Lazy;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class PreloadCacheService {

    private final ProductRepository productRepository;
    private final CategoryService categoryService;
    private final DiscountService discountService;
    private final SupplierService supplierService;

    @Async
    public void preloadProductCache() {
        try {
            productRepository.findAll(PageRequest.of(0, 20));
            productRepository.findByIsFeaturedTrue();
            productRepository.findAllByOrderByIdDesc();
            productRepository.findTop5ByOrderBySoldQuantityDesc();
            log.info("Preloaded product cache successfully (direct from repository)!");
        } catch (Exception e) {
            log.error("Failed to preload product cache", e);
        }
    }

    @Async
    public void preloadCategoryCache() {
        try {
            categoryService.getAllCategories();
            log.info("Preloaded category cache successfully!");
        } catch (Exception e) {
            log.error("Failed to preload category cache", e);
        }
    }

    @Async
    public void preloadDiscountCache() {
        try {
            discountService.getActiveDiscounts(0.0, PageRequest.of(0, 20));
            log.info("Preloaded discount cache successfully!");
        } catch (Exception e) {
            log.error("Failed to preload discount cache", e);
        }
    }

    @Async
    public void preloadSupplierCache() {
        try {
            supplierService.getAllSuppliers();
            log.info("Preloaded supplier cache successfully!");
        } catch (Exception e) {
            log.error("Failed to preload supplier cache", e);
        }
    }
}