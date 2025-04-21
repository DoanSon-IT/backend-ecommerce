package com.sondv.phone.service;

import com.sondv.phone.dto.*;
import com.sondv.phone.entity.*;
import com.sondv.phone.repository.*;
import jakarta.persistence.criteria.Predicate;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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
    private final ShippingService shippingService;
    private final PaymentRepository paymentRepository;

    private static final Logger logger = LoggerFactory.getLogger(OrderService.class); // Thêm logger

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

        // Áp mã giảm giá (nếu có)
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

        // Shipping
        ShippingInfo shippingInfo = new ShippingInfo();
        shippingInfo.setOrder(order);
        shippingInfo.setAddress(orderRequest.getAddress());
        shippingInfo.setPhoneNumber(orderRequest.getPhoneNumber());
        shippingInfo.setCarrier(orderRequest.getCarrier());

        // Tính shippingFee và estimatedDelivery từ ShippingService
        ShippingEstimateDTO estimate = shippingService.estimateShipping(
                orderRequest.getAddress(),
                orderRequest.getCarrier()
        );
        shippingInfo.setShippingFee(estimate.getFee());
        shippingInfo.setEstimatedDelivery(estimate.getEstimatedDelivery());
        order.setShippingInfo(shippingInfo);

        // Tổng tiền cuối cùng
        BigDecimal shippingFee = estimate.getFee() != null ? estimate.getFee() : BigDecimal.ZERO;
        BigDecimal finalTotal = totalPriceBeforeDiscount.subtract(discountAmount).add(shippingFee);

        order.setTotalPrice(finalTotal);
        order.setShippingFee(shippingFee);
        if (appliedDiscount != null) {
            order.setDiscount(appliedDiscount);
        }

        // Chuẩn hóa paymentMethod
        PaymentMethod paymentMethod;
        try {
            paymentMethod = orderRequest.getPaymentMethod() != null
                    ? PaymentMethod.valueOf(orderRequest.getPaymentMethod().toUpperCase())
                    : PaymentMethod.COD;
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Phương thức thanh toán không hợp lệ: " + orderRequest.getPaymentMethod());
        }

        // Lưu Order
        order = orderRepository.save(order);

        // Tạo Payment
        Payment payment = new Payment();
        payment.setOrder(order);
        payment.setPaymentMethod(paymentMethod);
        payment.setStatus(PaymentStatus.PENDING);
        paymentRepository.save(payment);

        return order;
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

        if (!order.getCustomer().getUser().getId().equals(user.getId()) &&
                user.getRoles().stream().noneMatch(role -> role == RoleName.ADMIN || role == RoleName.STAFF)) {
            throw new RuntimeException("Bạn không có quyền hủy đơn hàng này!");
        }

        if (!(order.getStatus() == OrderStatus.PENDING )) {
            throw new RuntimeException("Đơn hàng này không thể hủy ở trạng thái hiện tại!");
        }

        order.setStatus(OrderStatus.CANCELLED);
        orderRepository.save(order);

        for (OrderDetail detail : order.getOrderDetails()) {
            inventoryService.adjustInventory(
                    detail.getProduct().getId(),
                    detail.getQuantity(),
                    "Hủy đơn hàng",
                    user.getId()
            );
        }

        return order;
    }

    @Transactional
    public Order updateOrderStatus(Long orderId, OrderStatus status, User user) {
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new RuntimeException("Không tìm thấy đơn hàng!"));

        if (status == OrderStatus.COMPLETED) {
            // Điều chỉnh inventory
            for (OrderDetail detail : order.getOrderDetails()) {
                inventoryService.adjustInventory(
                        detail.getProduct().getId(),
                        -detail.getQuantity(),
                        "Hoàn thành đơn hàng",
                        user.getId()
                );
            }
            // Cộng 1000 điểm
            Customer customer = order.getCustomer();
            int currentPoints = customer.getLoyaltyPoints() != null ? customer.getLoyaltyPoints() : 0;
            customer.setLoyaltyPoints(currentPoints + 1000);
            customerRepository.save(customer);
            logger.info("Cộng 1000 điểm cho khách hàng {} (ID: {}). Tổng điểm hiện tại: {}",
                    customer.getUser().getFullName(), customer.getId(), customer.getLoyaltyPoints());
        }

        order.setStatus(status);
        return orderRepository.save(order);
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
        dto.setShippingFee(order.getShippingFee());

        // Lấy paymentMethod từ Payment
        Payment payment = paymentRepository.findByOrderId(order.getId()).orElse(null);
        if (payment != null) {
            dto.setPaymentMethod(payment.getPaymentMethod().name());
        }

        // Các phần ánh xạ khác (customer, shippingInfo, orderDetails) giữ nguyên
        if (order.getCustomer() != null && order.getCustomer().getUser() != null) {
            CustomerInfoDTO customerDTO = new CustomerInfoDTO();
            customerDTO.setFullName(order.getCustomer().getUser().getFullName());
            customerDTO.setEmail(order.getCustomer().getUser().getEmail());
            dto.setCustomer(customerDTO);
        }

        if (order.getShippingInfo() != null) {
            ShippingInfoDTO shippingDTO = new ShippingInfoDTO();
            shippingDTO.setAddress(order.getShippingInfo().getAddress());
            shippingDTO.setPhoneNumber(order.getShippingInfo().getPhoneNumber());
            shippingDTO.setCarrier(order.getShippingInfo().getCarrier());
            shippingDTO.setShippingFee(order.getShippingInfo().getShippingFee());
            shippingDTO.setEstimatedDelivery(order.getShippingInfo().getEstimatedDelivery());
            dto.setShippingInfo(shippingDTO);
        }

        List<OrderDetailResponse> detailDTOs = order.getOrderDetails().stream().map(detail -> {
            OrderDetailResponse d = new OrderDetailResponse();
            d.setId(detail.getId());
            d.setProductId(detail.getProduct().getId());
            d.setProductName(detail.getProduct().getName());
            d.setQuantity(detail.getQuantity());
            d.setPrice(detail.getPrice());
            String productImageUrl = detail.getProduct().getImages().stream()
                    .findFirst()
                    .map(ProductImage::getImageUrl)
                    .orElse("/images/default.png");
            d.setProductImage(productImageUrl);

            if (detail.getReview() != null) {
                ReviewResponse review = new ReviewResponse();
                review.setRating(detail.getReview().getRating());
                review.setComment(detail.getReview().getComment());
                d.setReview(review);
            }

            return d;
        }).toList();

        dto.setOrderDetails(detailDTOs);
        return dto;
    }
}