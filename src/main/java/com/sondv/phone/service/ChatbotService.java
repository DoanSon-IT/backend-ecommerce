package com.sondv.phone.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sondv.phone.dto.ChatResponse;
import com.sondv.phone.entity.Message;
import com.sondv.phone.entity.Product;
import com.sondv.phone.repository.MessageRepository;
import com.sondv.phone.repository.ProductRepository;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.math.BigDecimal;
import java.text.Normalizer;
import java.text.NumberFormat;
import java.time.LocalTime;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class ChatbotService {

    private static final Logger logger = LoggerFactory.getLogger(ChatbotService.class);

    private final ProductRepository productRepository;
    private final MessageRepository messageRepository;
    private final OpenAiClient openAiClient;

    private final Long BOT_ID = 0L;
    private final Locale locale = new Locale("vi", "VN");
    private final Random random = new Random();

    private final Map<String, String> searchCache = new ConcurrentHashMap<>();
    private List<String> productNames;

    @Value("classpath:chatbot_config.json")
    private Resource configResource;
    private Map<String, Object> config;
    private Map<String, Pattern> intentPatterns;

    @PostConstruct
    public void init() throws IOException {
        productNames = productRepository.findAllProductNames();
        logger.info("Đã tải {} tên sản phẩm vào bộ nhớ cache", productNames.size());

        ObjectMapper mapper = new ObjectMapper();
        config = mapper.readValue(configResource.getInputStream(), Map.class);
        loadIntentPatterns();
        logger.info("Đã tải cấu hình chatbot từ chatbot_config.json");
    }

    private void loadIntentPatterns() {
        intentPatterns = new HashMap<>();
        Map<String, String> patterns = (Map<String, String>) config.get("intent_patterns");
        patterns.forEach((key, value) -> intentPatterns.put(key, Pattern.compile(value)));
    }

    public ChatResponse processUserMessage(Long userId, String userMessage) {
        LocalTime currentTime = LocalTime.now();
        boolean isEvening = currentTime.getHour() >= 18 || currentTime.getHour() < 5;
        String timeGreeting = isEvening ? "Chào buổi tối" : "Chào";

        long messageCount = messageRepository.countBySenderId(userId);
        boolean isNewUser = messageCount <= 2;

        String aiReply;
        List<Long> productIds = new ArrayList<>();

        String intent = detectIntent(userMessage);
        String extractedKeyword = extractProductKeyword(userMessage);

        if (intent.equals("greeting") && intentPatterns.get("product_inquiry").matcher(userMessage).matches()) {
            intent = "product_inquiry";
            logger.info("Phát hiện lời chào kèm sản phẩm, chuyển sang product_inquiry: {}", extractedKeyword);
        }

        List<String> greetings = (List<String>) config.get("greetings");
        List<String> promotions = (List<String>) config.get("promotions");
        Map<String, String> promptTemplates = (Map<String, String>) config.get("prompt_templates");

        if (intent.equals("greeting")) {
            String greeting = getRandomMessage(greetings);
            if (isNewUser) {
                greeting = timeGreeting + greeting.substring(greeting.indexOf(" ")) + " Rất vui được phục vụ anh/chị lần đầu! 🌟";
            }
            aiReply = greeting;

        } else if (intent.equals("promotion")) {
            aiReply = getRandomMessage(promotions);

        } else if (intent.equals("price_inquiry")) {
            List<Product> matchedProducts = findProductsByKeyword(extractedKeyword);
            if (!matchedProducts.isEmpty()) {
                productIds = matchedProducts.stream().map(Product::getId).collect(Collectors.toList());
                String prompt = buildPriceInquiryPrompt(userMessage, matchedProducts, promptTemplates.get("price_inquiry"));
                aiReply = openAiClient.ask(prompt);
            } else {
                aiReply = "Hiện em chưa có thông tin giá về sản phẩm \"" + extractedKeyword + "\" ạ. Anh/chị muốn tìm hiểu về sản phẩm khác không? Hoặc cho phép em giới thiệu một số mẫu điện thoại đang được ưa chuộng.";
            }

        } else if (intent.equals("comparison")) {
            String[] keywords = extractComparisonKeywords(userMessage);
            List<Product> products1 = productRepository.findTop2ByNameContainingIgnoreCase(keywords[0]);
            List<Product> products2 = productRepository.findTop2ByNameContainingIgnoreCase(keywords[1]);
            if (!products1.isEmpty() && !products2.isEmpty()) {
                productIds.add(products1.get(0).getId());
                productIds.add(products2.get(0).getId());
                String prompt = buildComparisonPrompt(userMessage, products1, products2, promptTemplates.get("comparison"));
                aiReply = openAiClient.ask(prompt);
            } else {
                aiReply = "Dạ em chưa có đủ thông tin để so sánh giữa \"" + keywords[0] + "\" và \"" + keywords[1] + "\" ạ. Anh/chị có thể cho em biết cụ thể hơn về dòng sản phẩm hoặc model mà anh/chị muốn so sánh không ạ?";
            }

        } else if (intent.equals("feature_inquiry")) {
            List<Product> matchedProducts = findProductsByKeyword(extractedKeyword);
            if (!matchedProducts.isEmpty()) {
                productIds = matchedProducts.stream().map(Product::getId).collect(Collectors.toList());
                String featureType = detectFeatureType(userMessage);
                String prompt = buildFeatureInquiryPrompt(userMessage, matchedProducts, featureType, promptTemplates.get("feature_inquiry"));
                aiReply = openAiClient.ask(prompt);
            } else {
                aiReply = "Dạ em chưa có thông tin chi tiết về tính năng của \"" + extractedKeyword + "\" ạ. Anh/chị muốn tìm hiểu sản phẩm khác không ạ?";
            }

        } else if (intent.equals("budget_inquiry")) {
            BigDecimal maxPrice = extractBudget(userMessage);
            List<Product> matchedProducts = productRepository.findBySellingPriceLessThan(maxPrice);
            if (!matchedProducts.isEmpty()) {
                matchedProducts.sort((p1, p2) -> p2.getSellingPrice().compareTo(p1.getSellingPrice()));
                List<Product> topProducts = matchedProducts.stream().limit(5).collect(Collectors.toList());
                productIds = topProducts.stream().map(Product::getId).collect(Collectors.toList());
                String prompt = buildBudgetInquiryPrompt(userMessage, topProducts, promptTemplates.get("budget_inquiry"));
                aiReply = openAiClient.ask(prompt);
            } else {
                aiReply = "Hiện không có sản phẩm nào trong tầm giá " + formatCurrency(maxPrice) + " ạ. Anh/chị có muốn xem các sản phẩm giá cao hơn một chút không ạ? Shop có nhiều chương trình trả góp ưu đãi đấy ạ.";
            }

        } else if (intent.equals("best_seller")) {
            List<Product> topSelling = productRepository.findTop3ByOrderBySoldQuantityDesc();
            if (!topSelling.isEmpty()) {
                productIds = topSelling.stream().map(Product::getId).collect(Collectors.toList());
                String prompt = buildBestSellerPrompt(userMessage, topSelling, promptTemplates.get("best_seller"));
                aiReply = openAiClient.ask(prompt);
            } else {
                aiReply = """
                    Dạ rất tiếc hiện tại em chưa có đủ dữ liệu về sản phẩm bán chạy 😭
                    Nhưng shop có nhiều mẫu hot đang được khách hàng quan tâm. Anh/chị muốn tư vấn theo phân khúc giá nào không ạ? 📱
                    """;
            }

        } else {
            List<Product> matchedProducts = findProductsByKeyword(extractedKeyword);
            if (!matchedProducts.isEmpty()) {
                productIds = matchedProducts.stream().map(Product::getId).collect(Collectors.toList());
                String prompt = buildProductInquiryPrompt(userMessage, matchedProducts, isNewUser, promptTemplates.get("product_inquiry"));
                aiReply = openAiClient.ask(prompt);
            } else {
                List<Product> similarProducts = findSimilarProducts(extractedKeyword);
                if (!similarProducts.isEmpty()) {
                    productIds = similarProducts.stream().map(Product::getId).collect(Collectors.toList());
                    String prompt = buildSimilarProductPrompt(userMessage, similarProducts, extractedKeyword, promptTemplates.get("product_inquiry"));
                    aiReply = openAiClient.ask(prompt);
                } else {
                    String prompt = buildNoProductPrompt(userMessage, isNewUser, promptTemplates.get("product_inquiry"));
                    aiReply = openAiClient.ask(prompt);
                }
            }
        }

        saveMessage(userId, BOT_ID, userMessage);
        saveMessage(BOT_ID, userId, aiReply);

        return new ChatResponse(aiReply, productIds);
    }

    private String getRandomMessage(List<String> messages) {
        return messages.get(random.nextInt(messages.size()));
    }

    private String detectIntent(String message) {
        String lower = normalizeText(message);
        Map<String, Integer> intentScores = new HashMap<>();
        intentPatterns.keySet().forEach(intent -> intentScores.put(intent, 0));

        intentPatterns.forEach((intent, pattern) -> {
            if (pattern.matcher(lower).find()) {
                intentScores.put(intent, intentScores.get(intent) + 5);
            }
        });

        if (lower.startsWith("giá") || lower.contains("giá bao nhiêu") || lower.contains("mấy tiền")) {
            intentScores.put("price_inquiry", intentScores.get("price_inquiry") + 10);
        }

        if (lower.contains(" hay ") || lower.contains(" với ") || lower.contains(" và ") && intentPatterns.get("comparison").matcher(lower).find()) {
            intentScores.put("comparison", intentScores.get("comparison") + 10);
        }

        String[] bestSellerKeywords = {"bán chạy", "phổ biến", "hot", "trend", "xu hướng", "best seller"};
        for (String keyword : bestSellerKeywords) {
            if (lower.contains(keyword)) {
                intentScores.put("best_seller", intentScores.get("best_seller") + 10);
            }
        }

        return intentScores.entrySet().stream()
                .max(Map.Entry.comparingByValue())
                .map(Map.Entry::getKey)
                .orElse("product_inquiry");
    }

    // Restored method: extractProductKeyword
    @Cacheable("searchKeywords")
    private String extractProductKeyword(String userMessage) {
        String phoneModel = extractPhoneModel(userMessage);
        if (phoneModel != null) {
            return phoneModel;
        }

        String[] brands = {"iphone", "samsung", "xiaomi", "oppo", "vivo", "realme"};
        for (String brand : brands) {
            if (userMessage.toLowerCase().contains(brand)) {
                Pattern modelPattern = Pattern.compile("(?i)" + brand + "\\s*(\\d+)\\s*(pro|plus|max|ultra|mini)?", Pattern.CASE_INSENSITIVE);
                Matcher matcher = modelPattern.matcher(userMessage);
                if (matcher.find()) {
                    String model = matcher.group(1);
                    String variant = matcher.group(2);
                    if (variant != null) {
                        return brand + " " + model + " " + variant;
                    }
                    return brand + " " + model;
                }
                return brand;
            }
        }

        for (String productName : productNames) {
            if (userMessage.toLowerCase().contains(productName.toLowerCase())) {
                return productName;
            }
        }

        return advancedProductTokenization(userMessage);
    }

    private String advancedProductTokenization(String userMessage) {
        String cleaned = userMessage.replaceAll("[^a-zA-Z0-9À-ỹ ]", "").toLowerCase();
        List<String> tokens = Arrays.stream(cleaned.split(" "))
                .sorted((a, b) -> Integer.compare(b.length(), a.length()))
                .collect(Collectors.toList());

        for (String token : tokens) {
            if (token.length() < 3) continue;
            Optional<String> match = productNames.stream()
                    .filter(name -> name.toLowerCase().contains(token))
                    .findFirst();
            if (match.isPresent()) {
                return match.get();
            }
        }

        if (cleaned.contains("điện thoại") || cleaned.contains("smartphone") || cleaned.contains("mobile")) {
            return extractBrandFromGenericQuery(cleaned);
        }

        return "iphone";
    }

    private String extractBrandFromGenericQuery(String query) {
        String[] keywords = {"iphone", "samsung", "xiaomi", "oppo", "vivo", "realme", "redmi"};
        for (String keyword : keywords) {
            if (query.contains(keyword)) {
                return keyword;
            }
        }
        return "iphone";
    }

    private String extractPhoneModel(String text) {
        Pattern[] patterns = {
                Pattern.compile("(?i)(iphone|samsung|xiaomi|oppo|vivo)[\\s-]*(\\d+)(?:[\\s-]*(pro|plus|max|ultra))?"),
                Pattern.compile("(?i)(galaxy|redmi|note)[\\s-]*(\\d+)(?:[\\s-]*(pro|plus|max|ultra))?"),
                Pattern.compile("(?i)(a|s|m)(\\d+)(?:[\\s-]*(s|plus|pro|ultra|fe))?")
        };

        for (Pattern pattern : patterns) {
            Matcher matcher = pattern.matcher(text);
            if (matcher.find()) {
                StringBuilder model = new StringBuilder(matcher.group(1));
                model.append(" ").append(matcher.group(2));
                if (matcher.groupCount() >= 3 && matcher.group(3) != null) {
                    model.append(" ").append(matcher.group(3));
                }
                return model.toString();
            }
        }
        return null;
    }

    // Restored method: findProductsByKeyword
    private List<Product> findProductsByKeyword(String keyword) {
        List<Product> results = productRepository.findByNameContainingIgnoreCase(keyword);
        if (!results.isEmpty()) {
            return results;
        }

        results = productRepository.findByNameContainingFlexible(keyword);
        if (!results.isEmpty()) {
            return results;
        }

        String[] brandModel = keyword.split(" ");
        if (brandModel.length >= 2) {
            results = productRepository.findByBrandAndModel(brandModel[0], brandModel[1]);
        }
        return results;
    }

    // Restored method: findSimilarProducts
    private List<Product> findSimilarProducts(String keyword) {
        if (keyword.toLowerCase().contains("iphone") || keyword.toLowerCase().contains("apple")) {
            return productRepository.findTop3ByNameContainingIgnoreCase("iphone");
        } else if (keyword.toLowerCase().contains("samsung") || keyword.toLowerCase().contains("galaxy")) {
            return productRepository.findTop3ByNameContainingIgnoreCase("samsung");
        } else if (keyword.toLowerCase().contains("xiaomi") || keyword.toLowerCase().contains("redmi")) {
            return productRepository.findTop3ByNameContainingIgnoreCase("xiaomi");
        } else if (keyword.toLowerCase().contains("oppo")) {
            return productRepository.findTop3ByNameContainingIgnoreCase("oppo");
        } else {
            return productRepository.findTop3ByOrderBySoldQuantityDesc();
        }
    }

    // Restored method: extractBudget
    private BigDecimal extractBudget(String userMessage) {
        Pattern pricePattern = Pattern.compile("(\\d+\\.?\\d*)\\s*(triệu|tr|trieu|million)");
        Matcher matcher = pricePattern.matcher(userMessage.toLowerCase());
        if (matcher.find()) {
            String priceStr = matcher.group(1);
            return new BigDecimal(priceStr).multiply(new BigDecimal("1000000"));
        }

        Pattern directPricePattern = Pattern.compile("(\\d{7,})");
        matcher = directPricePattern.matcher(userMessage);
        if (matcher.find()) {
            String priceStr = matcher.group(1);
            return new BigDecimal(priceStr);
        }

        return new BigDecimal("10000000");
    }

    // Restored method: normalizeText
    private String normalizeText(String text) {
        String normalized = Normalizer.normalize(text, Normalizer.Form.NFD);
        Pattern pattern = Pattern.compile("\\p{InCombiningDiacriticalMarks}+");
        return pattern.matcher(normalized).replaceAll("");
    }

    private String getProductHighlight(Product product) {
        String name = product.getName().toLowerCase();
        if (name.contains("iphone")) {
            return "Thiết kế sang trọng, hiệu năng mạnh mẽ";
        } else if (name.contains("samsung")) {
            if (name.contains("s") || name.contains("note") || name.contains("fold")) {
                return "Màn hình đẹp, camera chụp đêm xuất sắc";
            } else {
                return "Cân bằng giá/hiệu năng, pin trâu";
            }
        } else if (name.contains("xiaomi") || name.contains("redmi")) {
            return "Giá rẻ, cấu hình cao, pin trâu";
        } else if (name.contains("oppo")) {
            return "Camera selfie đỉnh, sạc siêu nhanh";
        } else if (name.contains("vivo")) {
            return "Camera chụp đêm tốt, thiết kế mỏng nhẹ";
        } else {
            return "Sản phẩm chất lượng cao";
        }
    }

    private String getProductFeature(Product product, String featureType) {
        String name = product.getName().toLowerCase();
        if (featureType.equals("Pin")) {
            if (name.contains("iphone")) {
                return "3000-4300mAh, sạc nhanh 20W";
            } else if (name.contains("samsung")) {
                return "4000-5000mAh, sạc nhanh 25W";
            } else {
                return "5000-6000mAh, sạc siêu nhanh 33W-67W";
            }
        } else if (featureType.equals("Camera")) {
            if (name.contains("iphone")) {
                return "12-48MP, Night Mode, Cinema Mode";
            } else if (name.contains("samsung")) {
                if (name.contains("s") || name.contains("note")) {
                    return "50-108MP, Night Mode, Space Zoom";
                } else {
                    return "32-64MP, chụp góc rộng";
                }
            } else {
                return "50-64MP, AI Camera, chụp đêm tốt";
            }
        } else if (featureType.equals("Màn hình")) {
            if (name.contains("iphone")) {
                return "OLED, Super Retina XDR";
            } else if (name.contains("samsung")) {
                if (name.contains("s") || name.contains("note")) {
                    return "Dynamic AMOLED 2X, 120Hz";
                } else {
                    return "Super AMOLED, 90Hz";
                }
            } else {
                return "AMOLED, 120Hz, DotDisplay";
            }
        } else if (featureType.equals("Hiệu năng")) {
            if (name.contains("iphone")) {
                return "Chip A-Series, hiệu năng hàng đầu";
            } else if (name.contains("samsung")) {
                if (name.contains("s") || name.contains("note")) {
                    return "Exynos/Snapdragon mới nhất";
                } else {
                    return "Exynos/Snapdragon dòng trung";
                }
            } else {
                return "MediaTek Dimensity/Snapdragon 8xx";
            }
        } else if (featureType.equals("Bộ nhớ")) {
            if (name.contains("iphone")) {
                return "128GB-1TB, không hỗ trợ thẻ nhớ";
            } else if (name.contains("samsung")) {
                return "128GB-512GB, hỗ trợ thẻ nhớ đến 1TB";
            } else {
                return "64GB-256GB, hỗ trợ thẻ nhớ đến 1TB";
            }
        } else {
            return "Tính năng hiện đại, phù hợp nhu cầu";
        }
    }

    private String detectFeatureType(String message) {
        String lower = message.toLowerCase();
        if (lower.contains("pin") || lower.contains("dung lượng") || lower.contains("sạc") || lower.contains("battery") || lower.contains("sac")) {
            return "Pin";
        }
        if (lower.contains("camera") || lower.contains("chụp") || lower.contains("quay") || lower.contains("selfie") || lower.contains("ảnh") || lower.contains("hình")) {
            return "Camera";
        }
        if (lower.contains("màn hình") || lower.contains("display") || lower.contains("screen") || lower.contains("man hinh")) {
            return "Màn hình";
        }
        if (lower.contains("cấu hình") || lower.contains("hiệu năng") || lower.contains("chip") || lower.contains("xử lý") || lower.contains("processor") || lower.contains("cau hinh") || lower.contains("cpu")) {
            return "Hiệu năng";
        }
        if (lower.contains("ram") || lower.contains("bộ nhớ") || lower.contains("memory") || lower.contains("lưu trữ") || lower.contains("storage") || lower.contains("bo nho")) {
            return "Bộ nhớ";
        }
        return "Tính năng";
    }

    private String[] extractComparisonKeywords(String userMessage) {
        String[] keywords = new String[2];
        String normalized = userMessage.toLowerCase()
                .replaceAll("(?i)so sánh", "")
                .replaceAll("(?i)giữa", "");

        if (normalized.contains(" với ")) {
            String[] parts = normalized.split(" với ");
            keywords[0] = parts[0].trim();
            keywords[1] = parts[1].trim();
        } else if (normalized.contains(" và ")) {
            String[] parts = normalized.split(" và ");
            keywords[0] = parts[0].trim();
            keywords[1] = parts[1].trim();
        } else if (normalized.contains(" hay ")) {
            String[] parts = normalized.split(" hay ");
            keywords[0] = parts[0].trim();
            keywords[1] = parts[1].trim();
        } else if (normalized.contains(" hoặc ")) {
            String[] parts = normalized.split(" hoặc ");
            keywords[0] = parts[0].trim();
            keywords[1] = parts[1].trim();
        } else {
            String[] words = normalized.split(" ");
            int midPoint = words.length / 2;
            StringBuilder part1 = new StringBuilder();
            for (int i = 0; i < midPoint; i++) {
                part1.append(words[i]).append(" ");
            }
            StringBuilder part2 = new StringBuilder();
            for (int i = midPoint; i < words.length; i++) {
                part2.append(words[i]).append(" ");
            }
            keywords[0] = part1.toString().trim();
            keywords[1] = part2.toString().trim();
        }
        return keywords;
    }

    private String buildProductInquiryPrompt(String userMessage, List<Product> products, boolean isNewUser, String template) {
        StringBuilder productData = new StringBuilder();
        for (Product p : products) {
            productData.append(String.format(
                    "- %s (ID:%d) | Giá: %s | %s | Kho: %d | Đã bán: %d\n",
                    p.getName(),
                    p.getId(),
                    formatCurrency(p.getSellingPrice()),
                    getProductHighlight(p),
                    p.getStock(),
                    p.getSoldQuantity()
            ));
        }
        return template.replace("{product_data}", productData.toString())
                .replace("{user_message}", userMessage);
    }

    private String buildPriceInquiryPrompt(String userMessage, List<Product> products, String template) {
        StringBuilder productData = new StringBuilder();
        for (Product p : products) {
            BigDecimal price = p.getDiscountedPrice() != null ? p.getDiscountedPrice() : p.getSellingPrice();
            productData.append(String.format(
                    "- %s | Giá hiện tại: %s | Kho: %d\n",
                    p.getName(),
                    formatCurrency(price),
                    p.getStock()
            ));
        }
        return template.replace("{product_data}", productData.toString())
                .replace("{user_message}", userMessage);
    }

    private String buildComparisonPrompt(String userMessage, List<Product> products1, List<Product> products2, String template) {
        StringBuilder products1Data = new StringBuilder();
        for (Product p : products1) {
            products1Data.append(String.format(
                    "- %s | Giá: %s | %s | Kho: %d\n",
                    p.getName(),
                    formatCurrency(p.getSellingPrice()),
                    getProductHighlight(p),
                    p.getStock()
            ));
        }
        StringBuilder products2Data = new StringBuilder();
        for (Product p : products2) {
            products2Data.append(String.format(
                    "- %s | Giá: %s | %s | Kho: %d\n",
                    p.getName(),
                    formatCurrency(p.getSellingPrice()),
                    getProductHighlight(p),
                    p.getStock()
            ));
        }
        return template.replace("{products1_data}", products1Data.toString())
                .replace("{products2_data}", products2Data.toString())
                .replace("{user_message}", userMessage);
    }

    private String buildFeatureInquiryPrompt(String userMessage, List<Product> products, String featureType, String template) {
        StringBuilder productData = new StringBuilder();
        for (int i = 0; i < Math.min(3, products.size()); i++) {
            Product p = products.get(i);
            productData.append(String.format(
                    "- %s | Giá: %s | %s: %s | Còn %d chiếc\n",
                    p.getName(),
                    formatCurrency(p.getSellingPrice()),
                    featureType,
                    getProductFeature(p, featureType),
                    p.getStock()
            ));
        }
        return template.replace("{product_data}", productData.toString())
                .replace("{user_message}", userMessage)
                .replace("{feature_type}", featureType);
    }

    private String buildBestSellerPrompt(String userMessage, List<Product> products, String template) {
        StringBuilder productData = new StringBuilder();
        for (int i = 0; i < Math.min(2, products.size()); i++) {
            Product p = products.get(i);
            BigDecimal price = p.getDiscountedPrice() != null ? p.getDiscountedPrice() : p.getSellingPrice();
            productData.append(String.format(
                    "- %s | Giá: %s | Đã bán: %d sp\n",
                    p.getName(),
                    formatCurrency(price),
                    p.getSoldQuantity()
            ));
        }
        return template.replace("{product_data}", productData.toString())
                .replace("{user_message}", userMessage);
    }

    // Restored method: buildBudgetInquiryPrompt
    private String buildBudgetInquiryPrompt(String userMessage, List<Product> products, String template) {
        StringBuilder productData = new StringBuilder();
        for (Product p : products) {
            productData.append(String.format(
                    "- %s | Giá: %s | %s\n",
                    p.getName(),
                    formatCurrency(p.getSellingPrice()),
                    getProductHighlight(p)
            ));
        }
        return template != null ? template.replace("{product_data}", productData.toString())
                .replace("{user_message}", userMessage) : "Default budget inquiry prompt.";
    }

    // Restored method: buildSimilarProductPrompt
    private String buildSimilarProductPrompt(String userMessage, List<Product> products, String keyword, String template) {
        StringBuilder productData = new StringBuilder();
        for (Product p : products) {
            productData.append(String.format(
                    "- %s | Giá: %s | %s\n",
                    p.getName(),
                    formatCurrency(p.getSellingPrice()),
                    getProductHighlight(p)
            ));
        }
        String customTemplate = "VAI TRÒ: Bạn là trợ lý bán hàng, hiện KHÔNG CÓ thông tin về sản phẩm \"" + keyword + "\".\n\n" +
                "CÂU HỎI KHÁCH HÀNG: \"{user_message}\"\n\n" +
                "DỮ LIỆU SẢN PHẨM TƯƠNG TỰ:\n{product_data}\n\n" +
                "YÊU CẦU PHẢN HỒI:\n" +
                "1. Xin lỗi vì không có thông tin sản phẩm được yêu cầu\n" +
                "2. Giới thiệu các sản phẩm tương tự từ danh sách trên\n" +
                "3. Mời khách xem các sản phẩm thay thế\n" +
                "4. Giữ thái độ tích cực và chuyên nghiệp\n" +
                "QUAN TRỌNG: KHÔNG ĐƯỢC GIẢ VỜ CÓ THÔNG TIN VỀ SẢN PHẨM KHÔNG TỒN TẠI";
        return customTemplate.replace("{product_data}", productData.toString())
                .replace("{user_message}", userMessage);
    }

    // Restored method: buildNoProductPrompt
    private String buildNoProductPrompt(String userMessage, boolean isNewUser, String template) {
        StringBuilder builder = new StringBuilder();
        List<Product> popularProducts = productRepository.findTop3ByOrderBySoldQuantityDesc();
        for (Product p : popularProducts) {
            builder.append(String.format(
                    "- %s | Giá: %s | %s\n",
                    p.getName(),
                    formatCurrency(p.getSellingPrice()),
                    getProductHighlight(p)
            ));
        }
        String customTemplate = "VAI TRÒ: Bạn là trợ lý bán hàng, hiện KHÔNG CÓ thông tin sản phẩm khách yêu cầu.\n\n" +
                "CÂU HỎI KHÁCH HÀNG: \"{user_message}\"\n\n" +
                "DỮ LIỆU SẢN PHẨM PHỔ BIẾN:\n{product_data}\n\n" +
                "YÊU CẦU PHẢN HỒI:\n" +
                "1. Xin lỗi vì không có thông tin sản phẩm được yêu cầu\n" +
                "2. CHỈ giới thiệu các sản phẩm phổ biến từ danh sách trên\n" +
                "3. Mời khách xem các sản phẩm thay thế\n" +
                "4. Giữ thái độ tích cực và chuyên nghiệp\n" +
                "QUAN TRỌNG: KHÔNG ĐƯỢC GIẢ VỜ CÓ THÔNG TIN VỀ SẢN PHẨM KHÔNG TỒN TẠI";
        return customTemplate.replace("{product_data}", builder.toString())
                .replace("{user_message}", userMessage);
    }

    private void saveMessage(Long senderId, Long receiverId, String content) {
        Message message = new Message();
        message.setSenderId(senderId);
        message.setReceiverId(receiverId);
        message.setContent(content);
        message.setRead(false);
        messageRepository.save(message);
    }

    private String formatCurrency(BigDecimal amount) {
        return NumberFormat.getCurrencyInstance(locale).format(amount);
    }
}