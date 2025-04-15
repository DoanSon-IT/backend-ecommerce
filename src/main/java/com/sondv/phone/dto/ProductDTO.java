package com.sondv.phone.dto;

import jakarta.validation.constraints.*;
import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.sondv.phone.model.Inventory;
import com.sondv.phone.model.InventoryLog;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Getter
@Setter
@ToString
@EqualsAndHashCode(of = "id")
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class ProductDTO {

    private Long id;

    @NotBlank(message = "Tên sản phẩm không được để trống")
    private String name;

    private String description;

    @NotNull(message = "Giá vốn không được để trống")
    @DecimalMin(value = "0.0", inclusive = false, message = "Giá vốn phải lớn hơn 0")
    private BigDecimal costPrice;

    @NotNull(message = "Giá bán không được để trống")
    @DecimalMin(value = "0.0", inclusive = false, message = "Giá bán phải lớn hơn 0")
    private BigDecimal sellingPrice;

    private BigDecimal discountedPrice;

    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd'T'HH:mm:ss")
    private LocalDateTime discountStartDate;

    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd'T'HH:mm:ss")
    private LocalDateTime discountEndDate;

    @JsonProperty("isFeatured")
    private boolean isFeatured = false;

    @Min(value = 0, message = "Tồn kho không được âm")
    private Integer stock;

    private Integer soldQuantity;

    @NotNull(message = "Danh mục không được để trống")
    private CategoryDTO category;

    @NotNull(message = "Nhà cung cấp không được để trống")
    private SupplierDTO supplier;

    private List<ProductImageDTO> images;

    private Inventory inventory;

    private List<InventoryLog> inventoryLogs;
}
