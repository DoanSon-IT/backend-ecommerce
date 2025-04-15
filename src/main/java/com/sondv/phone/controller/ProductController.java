package com.sondv.phone.controller;

import com.sondv.phone.dto.ProductDTO;
import com.sondv.phone.dto.ProductImageDTO;
import com.sondv.phone.model.*;
import com.sondv.phone.service.ProductService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/products")
@RequiredArgsConstructor
public class ProductController {

    private final ProductService productService;
    private static final Logger logger = LoggerFactory.getLogger(ProductController.class); // Sửa logger

    @GetMapping
    public ResponseEntity<Page<ProductDTO>> getAllProducts(
            @RequestParam(required = false, defaultValue = "") String searchKeyword,
            Pageable pageable) {
        try {
            Page<ProductDTO> products = productService.getAllProducts(searchKeyword, pageable);
            return ResponseEntity.ok(products);
        } catch (Exception e) {
            logger.error("Error fetching all products", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Page.empty(pageable));
        }
    }

    @GetMapping("/featured")
    public ResponseEntity<List<ProductDTO>> getFeaturedProducts(@RequestParam(defaultValue = "5") int limit) {
        try {
            if (limit <= 0) {
                return ResponseEntity.badRequest().body(List.of());
            }
            List<ProductDTO> products = productService.getFeaturedProducts();
            return ResponseEntity.ok(products);
        } catch (Exception e) {
            logger.error("Error fetching featured products", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(List.of());
        }
    }

    @GetMapping("/newest")
    public ResponseEntity<List<ProductDTO>> getNewestProducts(@RequestParam(defaultValue = "5") int limit) {
        try {
            if (limit <= 0) {
                return ResponseEntity.badRequest().body(List.of());
            }
            List<ProductDTO> products = productService.getNewestProducts(limit);
            return ResponseEntity.ok(products);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(List.of());
        } catch (Exception e) {
            logger.error("Error fetching newest products", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(List.of());
        }
    }

    @GetMapping("/bestselling")
    public ResponseEntity<List<ProductDTO>> getBestSellingProducts(@RequestParam(defaultValue = "5") int limit) {
        try {
            if (limit <= 0) {
                return ResponseEntity.badRequest().body(List.of());
            }
            List<ProductDTO> products = productService.getBestSellingProducts(limit);
            return ResponseEntity.ok(products);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(List.of());
        } catch (Exception e) {
            logger.error("Error fetching best-selling products", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(List.of());
        }
    }

    @GetMapping("/filtered")
    public ResponseEntity<Page<ProductDTO>> getFilteredProducts(
            @RequestParam(required = false) String searchKeyword,
            @RequestParam(required = false) BigDecimal minPrice,
            @RequestParam(required = false) BigDecimal maxPrice,
            @RequestParam(required = false) String sortBy,
            Pageable pageable) {
        try {
            if (minPrice != null && minPrice.compareTo(BigDecimal.ZERO) < 0) {
                logger.warn("Min price is negative");
                return ResponseEntity.ok(Page.empty(pageable)); // ✅ sửa ở đây
            }
            if (maxPrice != null && maxPrice.compareTo(BigDecimal.ZERO) < 0) {
                logger.warn("Max price is negative");
                return ResponseEntity.ok(Page.empty(pageable));
            }
            if (minPrice != null && maxPrice != null && minPrice.compareTo(maxPrice) > 0) {
                logger.warn("Min price > Max price");
                return ResponseEntity.ok(Page.empty(pageable));
            }

            Page<ProductDTO> products = productService.getFilteredProducts(searchKeyword, minPrice, maxPrice, sortBy, pageable);
            return ResponseEntity.ok(products);
        } catch (Exception e) {
            logger.error("Error fetching filtered products", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Page.empty(pageable));
        }
    }

    @GetMapping("/{id}")
    public ResponseEntity<ProductDTO> getProductById(@PathVariable String id) {
        try {
            Long productId = Long.parseLong(id);
            if (productId <= 0) {
                return ResponseEntity.badRequest().build();
            }
            Optional<ProductDTO> productDTO = productService.getProductById(productId);
            return productDTO.map(ResponseEntity::ok)
                    .orElseGet(() -> ResponseEntity.notFound().build());
        } catch (NumberFormatException e) {
            return ResponseEntity.badRequest().build();
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().build();
        } catch (Exception e) {
            logger.error("Error fetching product with ID: {}", id, e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<?> createProduct(@Valid @RequestBody ProductDTO productDTO) {
        try {
            Product product = mapToEntity(productDTO);
            ProductDTO savedProduct = productService.createProduct(product);
            return ResponseEntity.status(HttpStatus.CREATED).body(savedProduct);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        } catch (IllegalStateException e) {
            return ResponseEntity.status(HttpStatus.CONFLICT).body(e.getMessage());
        } catch (Exception e) {
            logger.error("Error creating product: {}", productDTO.getName(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("Lỗi server: " + e.getMessage());
        }
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<?> updateProduct(@PathVariable Long id, @Valid @RequestBody ProductDTO productDTO) {
        try {
            if (id == null || id <= 0) {
                return ResponseEntity.badRequest().body("ID sản phẩm không hợp lệ");
            }
            Product product = mapToEntity(productDTO);
            ProductDTO updatedProduct = productService.updateProduct(id, product);
            return ResponseEntity.ok(updatedProduct);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        } catch (Exception e) {
            logger.error("Error updating product with ID: {}", id, e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("Lỗi server: " + e.getMessage());
        }
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Void> deleteProduct(@PathVariable Long id) {
        try {
            if (id == null || id <= 0) {
                return ResponseEntity.badRequest().build();
            }
            productService.deleteProduct(id);
            return ResponseEntity.noContent().build();
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().build();
        } catch (Exception e) {
            logger.error("Error deleting product with ID: {}", id, e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    @PostMapping("/discount/all")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<String> applyDiscountToAll(
            @RequestParam BigDecimal percentage,
            @RequestParam(required = false) BigDecimal fixedAmount,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime startDateTime,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime endDateTime) {
        try {
            if (percentage == null && fixedAmount == null) {
                return ResponseEntity.badRequest().body("Phải cung cấp ít nhất một giá trị giảm giá (phần trăm hoặc cố định)");
            }
            if (percentage != null && percentage.compareTo(BigDecimal.ZERO) < 0) {
                return ResponseEntity.badRequest().body("Phần trăm giảm giá không được âm");
            }
            if (fixedAmount != null && fixedAmount.compareTo(BigDecimal.ZERO) < 0) {
                return ResponseEntity.badRequest().body("Số tiền giảm giá không được âm");
            }
            if (startDateTime == null || endDateTime == null || startDateTime.isAfter(endDateTime)) {
                return ResponseEntity.badRequest().body("Thời gian giảm giá không hợp lệ");
            }
            productService.applyDiscountToAll(percentage, fixedAmount, startDateTime, endDateTime);
            return ResponseEntity.ok("Đã áp dụng giảm giá cho tất cả sản phẩm!");
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        } catch (Exception e) {
            logger.error("Error applying discount to all products", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("Lỗi khi áp dụng giảm giá: " + e.getMessage());
        }
    }

    @PostMapping("/discount/selected")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<String> applyDiscountToSelected(
            @RequestBody List<Long> productIds,
            @RequestParam BigDecimal percentage,
            @RequestParam(required = false) BigDecimal fixedAmount,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime startDateTime,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime endDateTime) {
        try {
            if (productIds == null || productIds.isEmpty()) {
                return ResponseEntity.badRequest().body("Danh sách sản phẩm không được trống");
            }
            if (percentage == null && fixedAmount == null) {
                return ResponseEntity.badRequest().body("Phải cung cấp ít nhất một giá trị giảm giá (phần trăm hoặc cố định)");
            }
            if (percentage != null && percentage.compareTo(BigDecimal.ZERO) < 0) {
                return ResponseEntity.badRequest().body("Phần trăm giảm giá không được âm");
            }
            if (fixedAmount != null && fixedAmount.compareTo(BigDecimal.ZERO) < 0) {
                return ResponseEntity.badRequest().body("Số tiền giảm giá không được âm");
            }
            if (startDateTime == null || endDateTime == null || startDateTime.isAfter(endDateTime)) {
                return ResponseEntity.badRequest().body("Thời gian giảm giá không hợp lệ");
            }
            productService.applyDiscountToSelected(productIds, percentage, fixedAmount, startDateTime, endDateTime);
            return ResponseEntity.ok("Đã áp dụng giảm giá cho các sản phẩm được chọn!");
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        } catch (Exception e) {
            logger.error("Error applying discount to selected products", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("Lỗi khi áp dụng giảm giá: " + e.getMessage());
        }
    }

    @PostMapping("/{productId}/images")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<?> addProductImages(
            @PathVariable Long productId,
            @RequestParam("files") List<MultipartFile> files) {

        try {
            if (productId == null || productId <= 0) {
                return ResponseEntity.badRequest().body("ID sản phẩm không hợp lệ");
            }

            if (files == null || files.isEmpty()) {
                return ResponseEntity.badRequest().body("Danh sách ảnh không được trống");
            }

            List<ProductImageDTO> savedImages = files.stream()
                    .filter(file -> file != null && !file.isEmpty())
                    .map(file -> productService.addProductImage(productId, file))
                    .collect(Collectors.toList());

            return ResponseEntity.status(HttpStatus.CREATED).body(savedImages);

        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        } catch (Exception e) {
            logger.error("Error adding images for product ID: {}", productId, e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("Lỗi khi thêm ảnh: " + e.getMessage());
        }
    }

    @DeleteMapping("/images/{imageId}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Void> deleteProductImage(@PathVariable Long imageId) {
        try {
            if (imageId == null || imageId <= 0) {
                return ResponseEntity.badRequest().build();
            }
            productService.deleteProductImage(imageId);
            return ResponseEntity.noContent().build();
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().build();
        } catch (Exception e) {
            logger.error("Error deleting image with ID: {}", imageId, e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    private Product mapToEntity(ProductDTO productDTO) {
        Product product = new Product();
        product.setId(productDTO.getId());
        product.setName(productDTO.getName());
        product.setDescription(productDTO.getDescription());
        product.setCostPrice(productDTO.getCostPrice());
        product.setSellingPrice(productDTO.getSellingPrice());
        product.setDiscountedPrice(productDTO.getDiscountedPrice());
        product.setDiscountStartDate(productDTO.getDiscountStartDate());
        product.setDiscountEndDate(productDTO.getDiscountEndDate());
        product.setFeatured(productDTO.isFeatured());
        product.setStock(productDTO.getStock());
        product.setSoldQuantity(
                productDTO.getSoldQuantity() != null ? productDTO.getSoldQuantity() : 0
        );

        if (productDTO.getCategory() == null || productDTO.getCategory().getId() == null) {
            throw new IllegalArgumentException("ID danh mục là bắt buộc");
        }
        Category category = new Category();
        category.setId(productDTO.getCategory().getId());
        product.setCategory(category);

        if (productDTO.getSupplier() == null || productDTO.getSupplier().getId() == null) {
            throw new IllegalArgumentException("ID nhà cung cấp là bắt buộc");
        }
        Supplier supplier = new Supplier();
        supplier.setId(productDTO.getSupplier().getId());
        product.setSupplier(supplier);

        List<ProductImage> images = new ArrayList<>();
        if (productDTO.getImages() != null && !productDTO.getImages().isEmpty()) {
            images = productDTO.getImages().stream()
                    .filter(dto -> dto.getImageUrl() != null && !dto.getImageUrl().trim().isEmpty())
                    .map(dto -> {
                        ProductImage image = new ProductImage();
                        image.setImageUrl(dto.getImageUrl());
                        image.setProduct(product);
                        return image;
                    }).collect(Collectors.toList());
        }
        product.setImages(images);

        return product;
    }
}