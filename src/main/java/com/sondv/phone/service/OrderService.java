package com.sondv.phone.service;

import com.sondv.phone.dto.*;
import com.sondv.phone.model.*;
import com.sondv.phone.repository.*;
import jakarta.persistence.criteria.Predicate;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
public class OrderService {

    private final OrderRepository orderRepository;
    private final CustomerRepository customerRepository;
    private final DiscountRepository discountRepository;
    private final ProductRepository productRepository;
    private final OrderDetailRepository orderDetailRepository;
    private final InventoryService inventoryService;

    @Transactional
    public Order createOrder(User user, OrderRequest orderRequest) {
        if (orderRequest.getProductIds().size() != orderRequest.getQuantities().size()) {
            throw new IllegalArgumentException("Số lượng sản phẩm và số lượng không khớp.");
        }

        Customer customer = customerRepository.findByUserId(user.getId())
                .orElseGet(() -> {
                    Customer newCustomer = new Customer();
                    newCustomer.setUser(user);
                    return customerRepository.save(newCustomer);
                });

        Order order = new Order();
        order.setCustomer(customer);
        order.setStatus(OrderStatus.PENDING);
        order.setCreatedAt(LocalDateTime.now());

        List<OrderDetail> orderDetails = new ArrayList<>();
        BigDecimal totalPriceBeforeDiscount = BigDecimal.ZERO;
        OffsetDateTime now = OffsetDateTime.now();

        // ✅ Lặp qua các sản phẩm trong giỏ
        for (int i = 0; i < orderRequest.getProductIds().size(); i++) {
            Long productId = orderRequest.getProductIds().get(i);
            int quantity = orderRequest.getQuantities().get(i);

            Inventory inventory = inventoryService.getInventoryByProduct(productId)
                    .orElseThrow(() -> new RuntimeException("Không tìm thấy tồn kho cho sản phẩm ID: " + productId));

            if (inventory.getQuantity() < quantity) {
                throw new RuntimeException("Sản phẩm '" + inventory.getProduct().getName() + "' không đủ hàng.");
            }

            inventoryService.adjustInventory(productId, -quantity, "Tạo đơn hàng", user.getId());

            Product product = inventory.getProduct();

            // ❌ Không cho áp mã nếu có khuyến mãi đang hoạt động
            if (product.getDiscountedPrice() != null &&
                    product.getDiscountStartDate() != null &&
                    product.getDiscountEndDate() != null) {

                OffsetDateTime start = product.getDiscountStartDate().atOffset(ZoneOffset.UTC);
                OffsetDateTime end = product.getDiscountEndDate().atOffset(ZoneOffset.UTC);

                boolean isDiscountActive = !now.isBefore(start) && !now.isAfter(end);
                if (isDiscountActive && orderRequest.getDiscountCode() != null) {
                    throw new IllegalArgumentException("Sản phẩm '" + product.getName() + "' đang khuyến mãi, không thể áp mã.");
                }
            }

            BigDecimal price = product.getSellingPrice();
            OrderDetail detail = new OrderDetail();
            detail.setOrder(order);
            detail.setProduct(product);
            detail.setQuantity(quantity);
            detail.setPrice(price);
            orderDetails.add(detail);

            totalPriceBeforeDiscount = totalPriceBeforeDiscount.add(price.multiply(BigDecimal.valueOf(quantity)));
        }

        order.setOrderDetails(orderDetails);

        // ✅ Áp mã giảm giá (nếu có)
        Discount appliedDiscount = null;
        BigDecimal discountAmount = BigDecimal.ZERO;

        if (orderRequest.getDiscountCode() != null && !orderRequest.getDiscountCode().isBlank()) {
            appliedDiscount = discountRepository.findByCode(orderRequest.getDiscountCode())
                    .orElseThrow(() -> new IllegalArgumentException("❌ Mã giảm giá không tồn tại."));

            if (appliedDiscount.isUsed()) {
                throw new IllegalArgumentException("❌ Mã giảm giá đã được sử dụng.");
            }

            if (now.isBefore(appliedDiscount.getValidFrom())) {
                throw new IllegalArgumentException("⏳ Mã giảm giá chưa có hiệu lực.");
            }
            if (now.isAfter(appliedDiscount.getValidTo())) {
                throw new IllegalArgumentException("⛔ Mã giảm giá đã hết hạn.");
            }

            if (totalPriceBeforeDiscount.compareTo(BigDecimal.valueOf(appliedDiscount.getMinOrderValue())) < 0) {
                throw new IllegalArgumentException("🛒 Đơn hàng chưa đạt điều kiện tối thiểu để áp mã.");
            }

            discountAmount = totalPriceBeforeDiscount
                    .multiply(BigDecimal.valueOf(appliedDiscount.getDiscountPercentage()))
                    .divide(BigDecimal.valueOf(100));

            appliedDiscount.setUsed(true);
            discountRepository.save(appliedDiscount);
        }

        // ✅ Shipping
        ShippingInfo shippingInfo = new ShippingInfo();
        shippingInfo.setOrder(order);
        shippingInfo.setAddress(orderRequest.getAddress());
        shippingInfo.setPhoneNumber(orderRequest.getPhoneNumber());
        shippingInfo.setCarrier(orderRequest.getCarrier());
        shippingInfo.setShippingFee(orderRequest.getShippingFee());
        shippingInfo.setEstimatedDelivery(orderRequest.getEstimatedDelivery());
        order.setShippingInfo(shippingInfo);

        // ✅ Tổng tiền cuối cùng
        BigDecimal shippingFee = orderRequest.getShippingFee() != null ? orderRequest.getShippingFee() : BigDecimal.ZERO;
        BigDecimal finalTotal = totalPriceBeforeDiscount.subtract(discountAmount).add(shippingFee);

        order.setTotalPrice(finalTotal);
        if (appliedDiscount != null) {
            order.setDiscount(appliedDiscount);
        }

        return orderRepository.save(order);
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

    @Transactional
    public Order cancelOrder(Long orderId, User user) {
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new RuntimeException("Không tìm thấy đơn hàng!"));

        // Kiểm tra quyền (user chỉ được hủy đơn hàng của mình, hoặc Admin được quyền hủy bất kỳ đơn nào)
        if (!order.getCustomer().getUser().getId().equals(user.getId()) &&
                user.getRoles().stream().noneMatch(role -> role == RoleName.ADMIN || role == RoleName.STAFF)) {
            throw new RuntimeException("Bạn không có quyền hủy đơn hàng này!");
        }

        // Kiểm tra trạng thái cho phép hủy
        if (!(order.getStatus() == OrderStatus.PENDING )) {
            throw new RuntimeException("Đơn hàng này không thể hủy ở trạng thái hiện tại!");
        }

        // Cập nhật trạng thái đơn hàng thành CANCELLED
        order.setStatus(OrderStatus.CANCELLED);
        orderRepository.save(order);

        // Hoàn lại số lượng tồn kho
        for (OrderDetail detail : order.getOrderDetails()) {
            inventoryService.adjustInventory(
                    detail.getProduct().getId(),
                    detail.getQuantity(), // hoàn lại số lượng
                    "Hủy đơn hàng",
                    user.getId()
            );
        }

        return order;
    }

    public Page<OrderResponse> getPaginatedOrders(User user,
                                                  int page,
                                                  int size,
                                                  String sortField,
                                                  String sortDirection,
                                                  String status,
                                                  String customerName,
                                                  String orderId,
                                                  LocalDate startDate,
                                                  LocalDate endDate) {

        Sort.Direction direction = Sort.Direction.fromString(sortDirection.toUpperCase());
        Pageable pageable = PageRequest.of(page - 1, size, Sort.by(direction, sortField));

        Specification<Order> spec = (root, query, cb) -> {
            Predicate predicate = cb.conjunction();

            // Nếu không phải Admin/Staff → chỉ lấy đơn của khách hàng đó
            boolean isAdmin = user.getRoles().stream().anyMatch(r -> r == RoleName.ADMIN || r == RoleName.STAFF);
            if (!isAdmin) {
                Customer customer = customerRepository.findByUserId(user.getId())
                        .orElseThrow(() -> new RuntimeException("Không tìm thấy thông tin khách hàng!"));
                predicate = cb.and(predicate, cb.equal(root.get("customer").get("id"), customer.getId()));
            }

            if (status != null && !status.isBlank()) {
                predicate = cb.and(predicate, cb.equal(root.get("status"), OrderStatus.valueOf(status)));
            }

            if (customerName != null && !customerName.isBlank()) {
                predicate = cb.and(predicate,
                        cb.like(cb.lower(root.get("customer").get("user").get("fullName")),
                                "%" + customerName.toLowerCase() + "%"));
            }

            if (orderId != null && !orderId.isBlank()) {
                predicate = cb.and(predicate,
                        cb.like(cb.function("STR", String.class, root.get("id")),
                                "%" + orderId + "%"));
            }

            if (startDate != null) {
                predicate = cb.and(predicate, cb.greaterThanOrEqualTo(root.get("createdAt"),
                        startDate.atStartOfDay()));
            }

            if (endDate != null) {
                predicate = cb.and(predicate, cb.lessThanOrEqualTo(root.get("createdAt"),
                        endDate.plusDays(1).atStartOfDay()));
            }

            return predicate;
        };

        Page<Order> ordersPage = orderRepository.findAll(spec, pageable);
        return ordersPage.map(this::mapToOrderResponse);
    }

    public OrderResponse mapToOrderResponse(Order order) {
        OrderResponse dto = new OrderResponse();
        dto.setId(order.getId());
        dto.setStatus(order.getStatus().name());
        dto.setCreatedAt(order.getCreatedAt());
        dto.setTotalPrice(order.getTotalPrice());

        // ✅ Kiểm tra null tránh lỗi
        if (order.getCustomer() != null && order.getCustomer().getUser() != null) {
            CustomerInfoDTO customerDTO = new CustomerInfoDTO();
            customerDTO.setFullName(order.getCustomer().getUser().getFullName());
            customerDTO.setEmail(order.getCustomer().getUser().getEmail());
            dto.setCustomer(customerDTO);
        }

        // Shipping info
        if (order.getShippingInfo() != null) {
            ShippingInfoDTO shippingDTO = new ShippingInfoDTO();
            shippingDTO.setAddress(order.getShippingInfo().getAddress());
            shippingDTO.setPhoneNumber(order.getShippingInfo().getPhoneNumber());
            shippingDTO.setCarrier(order.getShippingInfo().getCarrier());
            shippingDTO.setShippingFee(order.getShippingInfo().getShippingFee());
            shippingDTO.setEstimatedDelivery(order.getShippingInfo().getEstimatedDelivery());
            dto.setShippingInfo(shippingDTO);
        }

        // Chi tiết đơn hàng
        List<OrderDetailResponse> detailDTOs = order.getOrderDetails().stream().map(detail -> {
            OrderDetailResponse d = new OrderDetailResponse();
            d.setId(detail.getId());
            d.setProductName(detail.getProduct().getName());
            d.setQuantity(detail.getQuantity());
            d.setPrice(detail.getPrice());
            String productImageUrl = detail.getProduct().getImages().stream()
                    .findFirst()
                    .map(ProductImage::getImageUrl)
                    .orElse("/images/default.png");
            d.setProductImage(productImageUrl);
            return d;
        }).toList();

        dto.setOrderDetails(detailDTOs);
        return dto;
    }
}
