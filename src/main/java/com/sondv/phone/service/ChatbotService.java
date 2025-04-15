package com.sondv.phone.service;

import com.sondv.phone.dto.ChatResponse;
import com.sondv.phone.model.Message;
import com.sondv.phone.model.Product;
import com.sondv.phone.repository.MessageRepository;
import com.sondv.phone.repository.ProductRepository;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.math.BigDecimal;
import java.text.Normalizer;
import java.text.NumberFormat;
import java.time.LocalTime;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;

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

    // Cache kết quả tìm kiếm
    private final Map<String, String> searchCache = new ConcurrentHashMap<>();
    private final RestTemplate restTemplate = new RestTemplate();

    // URL của API nội bộ
    private static final String PRODUCT_API_URL = "http://localhost:8080/api/products";

    // Danh sách các lời chào đa dạng
    private final List<String> greetings = Arrays.asList(
            "Xin chào anh/chị ạ! Em là trợ lý bán hàng của DoanSon Store 👋 Em có thể tư vấn giúp gì cho anh/chị hôm nay ạ?",
            "Chào anh/chị! Rất vui được hỗ trợ anh/chị 😊 Shop chúng em đang có nhiều ưu đãi hấp dẫn về điện thoại đấy ạ!",
            "Kính chào quý khách! 🌟 Em là trợ lý tại DoanSon Store, anh/chị đang tìm kiếm dòng điện thoại nào ạ?"
    );

    private final List<String> promotions = Arrays.asList(
            "🔥 HOT! Em xin chia sẻ ngay ạ: Hiện tại shop đang giảm đến 2 TRIỆU cho iPhone 14 Pro, Samsung A54 giảm 1.5 TRIỆU, và Redmi Note 12 giảm 800K! Anh/chị quan tâm đến dòng máy nào ạ? 🎁",
            "🎉 Dạ anh/chị đến đúng thời điểm rồi ạ! Shop đang có chương trình SỐC: Giảm ngay 2 TRIỆU cho iPhone 14 Pro, trả góp 0%, bảo hành VIP 18 tháng! Samsung và Xiaomi cũng đang có ưu đãi cực khủng. Em có thể tư vấn chi tiết hơn về sản phẩm nào ạ?",
            "⚡ SIÊU SALE tháng này anh/chị ơi! iPhone 14 Pro giảm 2 TRIỆU + tặng tai nghe AirPods, Samsung A54 giảm 1.5 TRIỆU + tặng ốp lưng xịn, Redmi Note 12 giảm 800K + tặng sạc dự phòng! Anh/chị muốn em gửi thông tin chi tiết về mẫu nào ạ? 💯"
    );

    private final List<String> unclear = Arrays.asList(
            "Dạ vâng, để em tư vấn chính xác nhất, anh/chị cho em hỏi anh/chị thường sử dụng điện thoại vào mục đích gì ạ? Chụp ảnh, chơi game, làm việc hay đa năng? Budget của anh/chị khoảng bao nhiêu ạ? 🎯",
            "Em rất vui được hỗ trợ ạ! 😊 Anh/chị có thể chia sẻ nhu cầu sử dụng chính và tầm giá đang cân nhắc không ạ? Từ đó em sẽ gợi ý những mẫu máy phù hợp nhất với anh/chị! 📱✨",
            "Dạ, để em tư vấn tốt nhất, anh/chị cho em biết anh/chị quan tâm đến yếu tố nào nhất khi chọn điện thoại ạ? Pin trâu, camera xịn, cấu hình mạnh hay thiết kế đẹp? Ngân sách anh/chị dự kiến là bao nhiêu ạ? 🔋📸💪"
    );

    private final Pattern greetingPattern = Pattern.compile("(?i).*(chào|hi|hello|alo|xin chào|chao|hey|kính chào|chào bạn).*");
    private final Pattern promotionPattern = Pattern.compile("(?i).*(giảm|khuyến mãi|ưu đãi|khuyến mại|sale|giá tốt|đang có|quà|tặng|trả góp|giảm giá|voucher).*");
    private final Pattern unclearPattern = Pattern.compile("(?i).*(tư vấn|không biết|gợi ý|nên mua|phù hợp|chọn|hỏi|giúp|góp ý|ý kiến|suggest|recommend).*");
    private final Pattern pricePattern = Pattern.compile("(?i).*(giá|bao nhiêu|giá cả|giá tiền|mấy triệu|tiền|chi phí).*");
    private final Pattern comparePattern = Pattern.compile("(?i).*(so sánh|hơn|thua|vs|versus|hay là|hay|hoặc|so với|tốt hơn).*");
    private final Pattern featurePattern = Pattern.compile("(?i).*(pin|camera|màn hình|cấu hình|chip|hiệu năng|chơi game|chụp|quay|selfie|dung lượng|ram|bộ nhớ|sạc).*");

    public ChatResponse processUserMessage(Long userId, String userMessage) {
        LocalTime currentTime = LocalTime.now();
        boolean isEvening = currentTime.getHour() >= 18 || currentTime.getHour() < 5;
        String timeGreeting = isEvening ? "Chào buổi tối" : "Chào";

        long messageCount = messageRepository.countBySenderId(userId);
        boolean isNewUser = messageCount <= 2;

        String intent = detectIntent(userMessage);
        String aiReply;

        if (intent.equals("greeting")) {
            String greeting = getRandomMessage(greetings);
            if (isNewUser) {
                greeting = timeGreeting + greeting.substring(greeting.indexOf(" ")) + " Rất vui được phục vụ anh/chị lần đầu! 🌟";
            }
            aiReply = greeting;

        } else if (intent.equals("promotion")) {
            aiReply = getRandomMessage(promotions);

        } else if (intent.equals("unclear")) {
            aiReply = getRandomMessage(unclear);

        } else if (intent.equals("price_inquiry")) {
            String keyword = extractProductKeyword(userMessage);
            List<Product> matchedProducts = productRepository.findTop3ByNameContainingIgnoreCase(keyword);

            String prompt = buildPriceInquiryPrompt(userMessage, matchedProducts);
            aiReply = openAiClient.ask(prompt);

        } else if (intent.equals("comparison")) {
            String[] keywords = extractComparisonKeywords(userMessage);
            List<Product> products1 = productRepository.findTop2ByNameContainingIgnoreCase(keywords[0]);
            List<Product> products2 = productRepository.findTop2ByNameContainingIgnoreCase(keywords[1]);

            String prompt = buildComparisonPrompt(userMessage, products1, products2);
            aiReply = openAiClient.ask(prompt);

        } else if (intent.equals("feature_inquiry")) {
            String keyword = extractProductKeyword(userMessage);
            List<Product> matchedProducts = productRepository.findTop3ByNameContainingIgnoreCase(keyword);

            String featureType = detectFeatureType(userMessage);
            String prompt = buildFeatureInquiryPrompt(userMessage, matchedProducts, featureType);
            aiReply = openAiClient.ask(prompt);

        } else if (intent.equals("best_seller")) {
            List<Product> topSelling = productRepository.findTop3ByOrderBySoldQuantityDesc();
            if (!topSelling.isEmpty()) {
                String prompt = buildBestSellerPrompt(userMessage, topSelling);
                aiReply = openAiClient.ask(prompt);
            } else {
                aiReply = """
Rất tiếc hiện tại chưa có sản phẩm nào đủ điều kiện "best seller" ạ 😢
Nhưng shop có nhiều mẫu hot đang được khách hàng quan tâm. Anh/chị muốn tư vấn theo phân khúc nào không ạ? 📱
""";
            }

        } else {
            String keyword = extractProductKeyword(userMessage);
            List<Product> matchedProducts = productRepository.findTop5ByNameContainingIgnoreCase(keyword);

            if (!matchedProducts.isEmpty()) {
                String prompt = buildProductInquiryPrompt(userMessage, matchedProducts, isNewUser);
                aiReply = openAiClient.ask(prompt);
            } else {
                String prompt = buildNoProductPrompt(userMessage, isNewUser);
                aiReply = openAiClient.ask(prompt);
            }
        }

        saveMessage(userId, BOT_ID, userMessage);
        saveMessage(BOT_ID, userId, aiReply);

        return new ChatResponse(aiReply);
    }

    @Cacheable("searchKeywords")
    private String extractProductKeyword(String userMessage) {
        // Kiểm tra cache
        String cacheKey = userMessage.toLowerCase();
        if (searchCache.containsKey(cacheKey)) {
            logger.info("Cache hit for: {}", userMessage);
            return searchCache.get(cacheKey);
        }

        // Chuẩn hóa cơ bản
        String cleanedMessage = userMessage
                .replaceAll("(?i)shop có|có.*không|mua|giá.*bao nhiêu|bao nhiêu tiền|giá cả|tư vấn|so sánh|với|hay là|cho tôi xem", "")
                .replaceAll("[^a-zA-Z0-9À-ỹ ]", "")
                .replaceAll("\\s+", " ")
                .trim()
                .toLowerCase();

        logger.info("Cleaned message: {}", cleanedMessage);

        // Tách thành tokens (1 từ, 2 từ, 3 từ)
        String[] words = cleanedMessage.split(" ");
        List<String> tokens = new ArrayList<>();
        for (int i = 1; i <= Math.min(3, words.length); i++) {
            for (int j = 0; j <= words.length - i; j++) {
                String token = String.join(" ", Arrays.copyOfRange(words, j, j + i));
                if (token.length() > 3) {
                    tokens.add(token);
                }
            }
        }

        logger.info("Tokens: {}", tokens);

        // Gọi API để tìm sản phẩm
        String bestMatch = cleanedMessage;
        int bestScore = Integer.MAX_VALUE;
        String bestProductName = cleanedMessage;

        for (String token : tokens) {
            try {
                // Gọi API /api/products và chỉ lấy content (danh sách sản phẩm)
                String url = PRODUCT_API_URL + "?searchKeyword=" + token + "&page=0&size=5";
                ResponseEntity<Map<String, Object>> response = restTemplate.exchange(
                        url,
                        HttpMethod.GET,
                        null,
                        new ParameterizedTypeReference<Map<String, Object>>() {}
                );

                Map<String, Object> responseBody = response.getBody();
                if (responseBody != null && responseBody.containsKey("content")) {
                    List<Map<String, Object>> productList = (List<Map<String, Object>>) responseBody.get("content");
                    for (Map<String, Object> product : productList) {
                        String productName = (String) product.get("name");
                        if (productName != null) {
                            String normalizedProductName = normalizeVietnamese(productName.toLowerCase());
                            String normalizedToken = normalizeVietnamese(token);
                            int distance = levenshteinDistance(normalizedToken, normalizedProductName);

                            if (distance < bestScore && distance <= normalizedToken.length() / 2) {
                                bestScore = distance;
                                bestMatch = token;
                                bestProductName = productName;
                            }
                        }
                    }
                } else {
                    logger.debug("No products found for token: {}", token);
                }
            } catch (Exception e) {
                logger.warn("Error calling product API for token {}: {}", token, e.getMessage());
            }
        }

        // Chuẩn hóa lại bestProductName để loại bỏ từ không liên quan
        bestProductName = bestProductName
                .replaceAll("(?i)shop có|có.*không|mua|giá.*bao nhiêu|bao nhiêu tiền|giá cả|tư vấn|so sánh|với|hay là|cho tôi xem", "")
                .replaceAll("[^a-zA-Z0-9À-ỹ ]", "")
                .replaceAll("\\s+", " ")
                .trim();

        // Lưu vào cache
        searchCache.put(cacheKey, bestProductName);
        logger.info("Best matched product: {}", bestProductName);

        return bestProductName;
    }

    // Thuật toán Levenshtein
    private int levenshteinDistance(String s1, String s2) {
        int[][] dp = new int[s1.length() + 1][s2.length() + 1];

        for (int i = 0; i <= s1.length(); i++) {
            for (int j = 0; j <= s2.length(); j++) {
                if (i == 0) {
                    dp[i][j] = j;
                } else if (j == 0) {
                    dp[i][j] = i;
                } else {
                    dp[i][j] = Math.min(
                            Math.min(dp[i - 1][j] + 1, dp[i][j - 1] + 1),
                            dp[i - 1][j - 1] + (s1.charAt(i - 1) == s2.charAt(j - 1) ? 0 : 1)
                    );
                }
            }
        }

        return dp[s1.length()][s2.length()];
    }

    private String getRandomMessage(List<String> messages) {
        return messages.get(random.nextInt(messages.size()));
    }

    private String detectIntent(String message) {
        String lower = message.toLowerCase();

        if (greetingPattern.matcher(lower).matches()) {
            return "greeting";
        }
        if (promotionPattern.matcher(lower).matches()) {
            return "promotion";
        }
        if (pricePattern.matcher(lower).matches()) {
            return "price_inquiry";
        }
        if (comparePattern.matcher(lower).matches()) {
            return "comparison";
        }
        if (featurePattern.matcher(lower).matches()) {
            return "feature_inquiry";
        }
        if (unclearPattern.matcher(lower).matches()) {
            return "unclear";
        }

        return "product_inquiry";
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

    private String detectFeatureType(String message) {
        String lower = message.toLowerCase();

        if (lower.contains("pin") || lower.contains("dung lượng") || lower.contains("sạc") || lower.contains("battery")) {
            return "Pin";
        }
        if (lower.contains("camera") || lower.contains("chụp") || lower.contains("quay") || lower.contains("selfie")) {
            return "Camera";
        }
        if (lower.contains("màn hình") || lower.contains("display") || lower.contains("screen")) {
            return "Màn hình";
        }
        if (lower.contains("cấu hình") || lower.contains("hiệu năng") || lower.contains("chip") || lower.contains("xử lý") || lower.contains("processor")) {
            return "Hiệu năng";
        }
        if (lower.contains("ram") || lower.contains("bộ nhớ") || lower.contains("memory") || lower.contains("lưu trữ") || lower.contains("storage")) {
            return "Bộ nhớ";
        }

        return "Tính năng";
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

    private String normalizeVietnamese(String text) {
        String normalized = Normalizer.normalize(text, Normalizer.Form.NFD);
        Pattern pattern = Pattern.compile("\\p{InCombiningDiacriticalMarks}+");
        return pattern.matcher(normalized).replaceAll("");
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

    private String buildProductInquiryPrompt(String userMessage, List<Product> products, boolean isNewUser) {
        StringBuilder builder = new StringBuilder();
        for (Product p : products) {
            builder.append(String.format(
                    "- %s (ID: %d) | Giá: %s | Còn %d chiếc | 💎 %s\n",
                    p.getName(),
                    p.getId(),
                    formatCurrency(p.getSellingPrice()),
                    p.getStock(),
                    getProductHighlight(p)
            ));
        }

        return String.format("""
Người dùng hỏi: "%s"

📦 Dưới đây là sản phẩm phù hợp:

%s

💁‍♀️ HƯỚNG DẪN TƯ VẤN:
1. Trả lời như nhân viên bán hàng CHUYÊN NGHIỆP & THÂN THIỆN. Gọi khách là "anh/chị".
2. LUÔN ĐỀ XUẤT 1-2 mẫu cụ thể từ danh sách trên, kèm 2-3 ưu điểm nổi bật.
3. Nhấn mạnh các ưu đãi: "Trả góp 0%%", "Bảo hành 18 tháng", "Tặng phụ kiện chính hãng".
4. KẾT THÚC bằng câu hỏi gợi mở để khách mua ngay: "Anh/chị muốn em đặt hàng luôn không ạ?" hoặc "Anh/chị có thể ghé shop xem trực tiếp, em sẽ tư vấn tận tình!"
5. Sử dụng emoji phù hợp nhưng không quá lạm dụng.
6. %s

Trả lời ngắn gọn, dưới 150 từ, không liệt kê thông số kỹ thuật dài dòng.
""", userMessage, builder, isNewUser ? "Đây là khách hàng MỚI, hãy nhiệt tình HƠN NỮA!" : "Thể hiện sự chuyên nghiệp và am hiểu về sản phẩm.");
    }

    private String buildNoProductPrompt(String userMessage, boolean isNewUser) {
        return String.format("""
Người dùng hỏi: "%s"

⚠️ Không tìm thấy sản phẩm cụ thể. 

💁‍♀️ HƯỚNG DẪN TƯ VẤN:
1. Xin lỗi khách một cách CHUYÊN NGHIỆP vì không có sản phẩm họ đang tìm.
2. ĐỀ XUẤT 2-3 sản phẩm TƯƠNG TỰ hoặc THAY THẾ tốt nhất trong cùng phân khúc/hãng.
3. Gợi ý khách để lại SỐ ĐIỆN THOẠI để được thông báo ngay khi có hàng.
4. Nhấn mạnh: "Shop có dịch vụ đặt trước với ưu đãi đặc biệt"
5. KẾT THÚC với lời mời ghé thăm shop để được tư vấn trực tiếp về các sản phẩm thay thế tốt nhất.
6. %s

Trả lời thân thiện, chuyên nghiệp, sử dụng ít emoji phù hợp.
""", userMessage, isNewUser ? "Đây là khách hàng MỚI, hãy ÂN CẦN và NHIỆT TÌNH hơn!" : "Thể hiện sự thông cảm và mong muốn hỗ trợ.");
    }

    private String buildPriceInquiryPrompt(String userMessage, List<Product> products) {
        StringBuilder builder = new StringBuilder();
        for (Product p : products) {
            BigDecimal price = p.getDiscountedPrice() != null ? p.getDiscountedPrice() : p.getSellingPrice();
            BigDecimal original = p.getDiscountedPrice() != null ? p.getSellingPrice() : p.getCostPrice();

            builder.append(String.format(
                    "- %s | Giá khuyến mãi: %s | Giá gốc: %s | Còn %d sp\n",
                    p.getName(),
                    formatCurrency(price),
                    formatCurrency(original),
                    p.getStock()
            ));
        }

        return String.format("""
Người dùng hỏi về GIÁ: "%s"

💰 Thông tin giá sản phẩm:

%s

💁‍♀️ HƯỚNG DẪN TƯ VẤN:
1. Trả lời NHƯ NHÂN VIÊN BÁN HÀNG THỰC THỤ, gọi khách là "anh/chị".
2. Nhấn mạnh giá KHUYẾN MÃI hơn giá gốc.
3. Nêu rõ các HÌNH THỨC THANH TOÁN: "Trả góp 0%%", "Thanh toán qua thẻ giảm thêm 5%%".
4. Nhấn mạnh tính KHAN HIẾM: "Chỉ còn X chiếc với giá này", "Ưu đãi có thời hạn".
5. ĐỀ XUẤT mua ngay: "Anh/chị muốn em giữ máy không ạ?" hoặc "Em có thể tư vấn thêm về thủ tục trả góp nếu anh/chị quan tâm!"

Sử dụng emoji phù hợp, tạo cảm giác thân thiện nhưng vẫn chuyên nghiệp.
""", userMessage, builder);
    }

    private String buildComparisonPrompt(String userMessage, List<Product> products1, List<Product> products2) {
        StringBuilder builder = new StringBuilder();

        builder.append("📱 SẢN PHẨM NHÓM 1:\n");
        for (Product p : products1) {
            builder.append(String.format(
                    "- %s | Giá: %s | 💎 %s\n",
                    p.getName(),
                    formatCurrency(p.getSellingPrice()),
                    getProductHighlight(p)
            ));
        }

        builder.append("\n📱 SẢN PHẨM NHÓM 2:\n");
        for (Product p : products2) {
            builder.append(String.format(
                    "- %s | Giá: %s | 💎 %s\n",
                    p.getName(),
                    formatCurrency(p.getSellingPrice()),
                    getProductHighlight(p)
            ));
        }

        return String.format("""
Người dùng yêu cầu SO SÁNH: "%s"

%s

💁‍♀️ HƯỚNG DẪN TƯ VẤN:
1. SO SÁNH CHUYÊN NGHIỆP giữa hai nhóm sản phẩm, nêu bật ưu điểm của mỗi bên.
2. ĐỀ XUẤT RÕ RÀNG một lựa chọn tốt hơn dựa trên nhu cầu khách hàng.
3. Nhấn mạnh LỢI ÍCH cụ thể cho từng đối tượng sử dụng.
4. KẾT THÚC với gợi ý: "Anh/chị có thể dùng thử cả hai sản phẩm tại shop" hoặc "Em nghĩ [sản phẩm X] phù hợp hơn với nhu cầu của anh/chị, anh/chị có muốn đặt hàng luôn không ạ?"

Giọng điệu thân thiện, am hiểu, tự tin nhưng không áp đặt. Sử dụng emoji phù hợp.
""", userMessage, builder);
    }

    private String buildFeatureInquiryPrompt(String userMessage, List<Product> products, String featureType) {
        StringBuilder builder = new StringBuilder();
        for (Product p : products) {
            builder.append(String.format(
                    "- %s | Giá: %s | %s: %s\n",
                    p.getName(),
                    formatCurrency(p.getSellingPrice()),
                    featureType,
                    getProductFeature(p, featureType)
            ));
        }

        return String.format("""
Người dùng hỏi về TÍNH NĂNG %s: "%s"

📊 Thông tin sản phẩm:

%s

💁‍♀️ HƯỚNG DẪN TƯ VẤN:
1. Trả lời như CHUYÊN GIA về %s, giải thích rõ ưu điểm của mỗi sản phẩm.
2. Sử dụng ngôn ngữ DỄ HIỂU nhưng vẫn thể hiện kiến thức chuyên môn.
3. ĐỀ XUẤT cụ thể 1-2 sản phẩm tốt nhất về %s.
4. So sánh NGẮN GỌN với đối thủ cạnh tranh (nếu phù hợp).
5. KẾT THÚC với câu hỏi mở: "Anh/chị quan tâm đến model nào hơn ạ?" hoặc "Em có thể tư vấn chi tiết hơn về [sản phẩm được đề xuất] không ạ?"

Sử dụng 2-3 emoji phù hợp, trả lời chuyên nghiệp nhưng thân thiện.
""", featureType.toUpperCase(), userMessage, builder, featureType, featureType);
    }

    private String buildBestSellerPrompt(String userMessage, List<Product> products) {
        StringBuilder builder = new StringBuilder();
        for (Product p : products) {
            BigDecimal price = p.getDiscountedPrice() != null ? p.getDiscountedPrice() : p.getSellingPrice();
            builder.append(String.format("- %s | Giá: %s | Đã bán: %d sp\n",
                    p.getName(), formatCurrency(price), p.getSoldQuantity()));
        }

        return String.format("""
📈 Người dùng hỏi: "%s"

Dưới đây là các sản phẩm đang bán chạy nhất tại shop:

%s

🎯 Hãy trả lời thân thiện, khuyến khích mua hàng. Nếu sản phẩm phù hợp, mời chốt đơn nhẹ cái nha!
""", userMessage, builder);
    }
}