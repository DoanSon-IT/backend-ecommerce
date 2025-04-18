package com.sondv.phone.service;

import com.sondv.phone.dto.StatsResponse;
import com.sondv.phone.dto.TopProductDTO;
import com.sondv.phone.entity.Order;
import com.sondv.phone.entity.OrderStatus;
import com.sondv.phone.entity.Product;
import com.sondv.phone.entity.User;
import com.sondv.phone.repository.OrderDetailRepository;
import com.sondv.phone.repository.OrderRepository;
import com.sondv.phone.repository.ProductRepository;
import com.sondv.phone.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.Normalizer;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class AdminService {

    private final OrderRepository orderRepository;
    private final ProductRepository productRepository;
    private final UserRepository userRepository;
    private final OrderDetailRepository orderDetailRepository;

    public StatsResponse getDashboardStats(int days) {
        StatsResponse stats = new StatsResponse();
        LocalDate startDate = LocalDate.now().minusDays(days);
        LocalDateTime startDateTime = startDate.atStartOfDay();

        List<Order> orders = orderRepository.findByCreatedAtAfter(startDateTime)
                .stream()
                .filter(order -> order.getStatus() == OrderStatus.COMPLETED)
                .toList();
        BigDecimal totalRevenue = calculateTotalRevenue(orders);
        stats.setTotalRevenue(totalRevenue != null ? totalRevenue.setScale(2, RoundingMode.HALF_UP) : BigDecimal.ZERO);
        stats.setTotalOrders((long) orders.size());
        stats.setTopSellingProductsCount((long) productRepository.findBySoldQuantityGreaterThan(0).size());
        stats.setNewUsersCount((long) userRepository.findByCreatedAtAfter(startDateTime).size());

        Map<String, BigDecimal> revenueByTime = new LinkedHashMap<>();
        Map<String, Long> ordersByTime = new LinkedHashMap<>();
        for (LocalDate date = startDate; !date.isAfter(LocalDate.now()); date = date.plusDays(1)) {
            String dateKey = date.format(DateTimeFormatter.ISO_LOCAL_DATE);
            LocalDateTime dateStart = date.atStartOfDay();
            LocalDateTime dateEnd = date.atTime(23, 59, 59);
            List<Order> dailyOrders = orderRepository.findByCreatedAtBetweenAndStatus(dateStart, dateEnd, OrderStatus.COMPLETED);
            BigDecimal dailyRevenue = calculateTotalRevenue(dailyOrders);
            revenueByTime.put(dateKey, dailyRevenue != null ? dailyRevenue : BigDecimal.ZERO);
            ordersByTime.put(dateKey, (long) dailyOrders.size());
        }
        stats.setRevenueByTime(revenueByTime);
        stats.setOrdersByTime(ordersByTime);

        return stats;
    }

    public BigDecimal getTotalProfit(LocalDateTime startDateTime) {
        List<Order> orders = orderRepository.findByCreatedAtAfter(startDateTime)
                .stream()
                .filter(order -> order.getStatus() == OrderStatus.COMPLETED)
                .toList();
        BigDecimal totalProfit = BigDecimal.ZERO;

        for (Order order : orders) {
            for (var detail : order.getOrderDetails()) {
                BigDecimal sellPrice = detail.getPrice();
                BigDecimal costPrice = detail.getProduct().getCostPrice(); // assume có trường này
                BigDecimal profit = sellPrice.subtract(costPrice).multiply(BigDecimal.valueOf(detail.getQuantity()));
                totalProfit = totalProfit.add(profit);
            }
        }

        return totalProfit.setScale(2, RoundingMode.HALF_UP);
    }

    public BigDecimal calculateTotalRevenue(List<Order> orders) {
        if (orders == null || orders.isEmpty()) return BigDecimal.ZERO;
        return orders.stream()
                .map(order -> order.getOrderDetails().stream()
                        .map(detail -> detail.getPrice().multiply(BigDecimal.valueOf(detail.getQuantity())))
                        .reduce(BigDecimal.ZERO, BigDecimal::add))
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    public List<Order> getRecentOrders(int limit) {
        return orderRepository.findTopNByOrderByCreatedAtDesc(limit);
    }

    public List<Product> getTopSellingProducts(int limit) {
        return productRepository.findTopNByOrderBySoldQuantityDesc(PageRequest.of(0, limit));
    }

    public List<User> getRecentUsers(int limit) {
        return userRepository.findTopNByOrderByCreatedAtDesc(limit);
    }

    public List<TopProductDTO> getTopSellingProductsDTO(LocalDateTime startDate, LocalDateTime endDate, int limit) {
        Pageable pageable = PageRequest.of(0, limit);
        return orderDetailRepository.findTopSellingProducts(startDate, endDate, pageable);
    }

    public Map<String, Long> getOrderCountByStatus() {
        return Arrays.stream(com.sondv.phone.entity.OrderStatus.values())
                .collect(Collectors.toMap(
                        Enum::name,
                        status -> orderRepository.countByStatus(status)
                ));
    }

    public List<Map<String, Object>> getLowStockProducts(int threshold) {
        return productRepository.findByStockLessThan(threshold).stream()
                .map(p -> {
                    Map<String, Object> map = new HashMap<>();
                    map.put("productId", p.getId());
                    map.put("name", p.getName());
                    map.put("stock", p.getStock());
                    return map;
                })
                .collect(Collectors.toList());
    }

    public Map<String, Long> getUserCountByRegion() {
        List<User> users = userRepository.findAll();
        Map<String, Long> regionStats = new HashMap<>();

        for (User user : users) {
            String region = detectRegion(user.getAddress());
            if (region == null || region.equals("null")) continue;

            // Chuyển tên region về định dạng camelCase frontend đang dùng
            String normalizedRegion = switch (region) {
                case "central-highlands" -> "centralHighlands";
                default -> region;
            };

            regionStats.put(normalizedRegion, regionStats.getOrDefault(normalizedRegion, 0L) + 1);
        }

        return regionStats;
    }

    private String normalizeVietnamese(String input) {
        if (input == null) return null;
        String normalized = Normalizer.normalize(input, Normalizer.Form.NFD);
        return normalized.replaceAll("\\p{M}", "") // xoá dấu
                .replaceAll("đ", "d")
                .replaceAll("Đ", "D");
    }

    private String detectRegion(String address) {
        if (address == null || address.isEmpty()) return "foreign";

        address = normalizeVietnamese(address);

        address = address.toLowerCase()
                .replaceAll("tp\\.?|thanh pho", "")
                .replaceAll("[^a-z\\s]", "")
                .replaceAll("\\s+", " ")
                .trim();

        String[] north = {
                "ha noi", "hai phong", "bac ninh", "bac giang", "thai nguyen", "phu tho", "vinh phuc",
                "lang son", "cao bang", "tuyen quang", "ha giang", "yen bai", "lao cai", "son la",
                "dien bien", "hoa binh", "nam dinh", "thai binh", "ninh binh", "quang ninh", "bac kan",
                "hung yen", "ha nam", "hai duong"
        };

        String[] central = {
                "thanh hoa", "nghe an", "ha tinh", "quang binh", "quang tri", "thua thien hue", "hue",
                "da nang", "quang nam", "quang ngai", "binh dinh", "phu yen", "khanh hoa", "ninh thuan", "binh thuan"
        };

        String[] centralHighlands = {
                "kon tum", "gia lai", "dak lak", "dak nong", "lam dong"
        };

        String[] south = {
                "ho chi minh", "sai gon", "can tho", "binh duong", "binh phuoc", "dong nai", "tay ninh",
                "ba ria vung tau", "vung tau", "long an", "tien giang", "ben tre", "tra vinh", "vinh long",
                "dong thap", "an giang", "kien giang", "hau giang", "soc trang", "bac lieu", "ca mau"
        };

        for (String p : north) if (address.contains(p)) return "north";
        for (String p : central) if (address.contains(p)) return "central";
        for (String p : centralHighlands) if (address.contains(p)) return "centralHighlands";
        for (String p : south) if (address.contains(p)) return "south";

        return "unknown";
    }
}
