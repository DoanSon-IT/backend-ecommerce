package com.sondv.phone.service;

import com.sondv.phone.dto.ShippingEstimateDTO;
import com.sondv.phone.entity.Order;
import com.sondv.phone.entity.OrderStatus;
import com.sondv.phone.entity.ShippingInfo;
import com.sondv.phone.repository.OrderRepository;
import com.sondv.phone.repository.ShippingRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class ShippingService {

    private final ShippingRepository shippingRepository;
    private final OrderRepository orderRepository;
    private static final List<String> VALID_CARRIERS = List.of("GHN", "GHTK", "VNPOST");

    // Bảng phí tĩnh theo vùng và carrier
    private static final Map<String, Map<String, BigDecimal>> SHIPPING_FEES = new HashMap<>();
    private static final Map<String, Integer> DELIVERY_DAYS = new HashMap<>();

    static {
        // Nội thành (Hà Nội, TP.HCM)
        Map<String, BigDecimal> urbanFees = new HashMap<>();
        urbanFees.put("GHN", new BigDecimal("25000"));
        urbanFees.put("GHTK", new BigDecimal("20000"));
        urbanFees.put("VNPOST", new BigDecimal("30000"));
        SHIPPING_FEES.put("URBAN", urbanFees);

        // Ngoại thành
        Map<String, BigDecimal> suburbanFees = new HashMap<>();
        suburbanFees.put("GHN", new BigDecimal("35000"));
        suburbanFees.put("GHTK", new BigDecimal("30000"));
        suburbanFees.put("VNPOST", new BigDecimal("40000"));
        SHIPPING_FEES.put("SUBURBAN", suburbanFees);

        // Tỉnh khác
        Map<String, BigDecimal> remoteFees = new HashMap<>();
        remoteFees.put("GHN", new BigDecimal("50000"));
        remoteFees.put("GHTK", new BigDecimal("45000"));
        remoteFees.put("VNPOST", new BigDecimal("55000"));
        SHIPPING_FEES.put("REMOTE", remoteFees);

        // Thời gian giao hàng (ngày)
        DELIVERY_DAYS.put("URBAN", 1);
        DELIVERY_DAYS.put("SUBURBAN", 2);
        DELIVERY_DAYS.put("REMOTE", 4);
    }

    public ShippingEstimateDTO estimateShipping(String address, String carrier) {
        if (!VALID_CARRIERS.contains(carrier)) {
            throw new RuntimeException("Nhà vận chuyển không hợp lệ!");
        }

        // Phân loại vùng dựa trên address
        String region = determineRegion(address);
        BigDecimal fee = SHIPPING_FEES.get(region).get(carrier);
        int deliveryDays = DELIVERY_DAYS.get(region);
        LocalDateTime estimatedDelivery = LocalDateTime.now().plusDays(deliveryDays);

        ShippingEstimateDTO result = new ShippingEstimateDTO();
        result.setFee(fee);
        result.setEstimatedDelivery(estimatedDelivery);
        return result;
    }

    private String determineRegion(String address) {
        String normalizedAddress = address.toLowerCase();
        if (normalizedAddress.contains("hà nội") || normalizedAddress.contains("tp.hcm")) {
            return "URBAN";
        } else if (normalizedAddress.contains("huyện") || normalizedAddress.contains("thị xã")) {
            return "SUBURBAN";
        } else {
            return "REMOTE";
        }
    }

    public ShippingInfo createShipping(Long orderId, String carrier, String address, String phoneNumber) {
        if (!VALID_CARRIERS.contains(carrier)) {
            throw new RuntimeException("Nhà vận chuyển không hợp lệ!");
        }

        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new RuntimeException("Đơn hàng không tồn tại!"));

        if (order.getShippingInfo() != null) {
            throw new RuntimeException("Đơn hàng này đã có thông tin vận chuyển!");
        }

        ShippingEstimateDTO estimate = estimateShipping(address, carrier);

        ShippingInfo shippingInfo = new ShippingInfo();
        shippingInfo.setOrder(order);
        shippingInfo.setCarrier(carrier);
        shippingInfo.setTrackingNumber("");
        shippingInfo.setAddress(address);
        shippingInfo.setPhoneNumber(phoneNumber);
        shippingInfo.setShippingFee(estimate.getFee());
        shippingInfo.setEstimatedDelivery(estimate.getEstimatedDelivery());

        order.setShippingInfo(shippingInfo);
        order.setStatus(OrderStatus.SHIPPED);
        orderRepository.save(order);

        return shippingRepository.save(shippingInfo);
    }

    public Optional<ShippingInfo> getShippingByOrderId(Long orderId) {
        return shippingRepository.findByOrderId(orderId);
    }

    public ShippingInfo updateShippingInfo(Long orderId, String carrier, String trackingNumber, LocalDateTime estimatedDelivery) {
        if (!VALID_CARRIERS.contains(carrier)) {
            throw new RuntimeException("Nhà vận chuyển không hợp lệ!");
        }

        ShippingInfo shippingInfo = shippingRepository.findByOrderId(orderId)
                .orElseThrow(() -> new RuntimeException("Không tìm thấy thông tin vận chuyển!"));

        shippingInfo.setCarrier(carrier);
        shippingInfo.setTrackingNumber(trackingNumber);
        shippingInfo.setEstimatedDelivery(estimatedDelivery);
        return shippingRepository.save(shippingInfo);
    }

    public void deleteShipping(Long orderId) {
        ShippingInfo shippingInfo = shippingRepository.findByOrderId(orderId)
                .orElseThrow(() -> new RuntimeException("Không tìm thấy thông tin vận chuyển!"));

        Order order = shippingInfo.getOrder();
        order.setShippingInfo(null);
        order.setStatus(OrderStatus.CANCELLED);
        orderRepository.save(order);
        shippingRepository.delete(shippingInfo);
    }
}