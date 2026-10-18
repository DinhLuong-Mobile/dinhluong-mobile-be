//package com.dinhluong.dlmstore.service;
//
//import com.dinhluong.dlmstore.entity.ChatbotInteraction;
//import com.dinhluong.dlmstore.repository.ChatbotInteractionRepository;
//import com.dinhluong.dlmstore.utils.GeminiClient;
//import com.dinhluong.dlmstore.dto.ChatProductDto;
//import com.dinhluong.dlmstore.dto.GeminiFilterCriteria;
//import com.dinhluong.dlmstore.dto.ProductAiDto;
//import com.dinhluong.dlmstore.dto.responses.AiFilterResponse;
//import com.dinhluong.dlmstore.dto.responses.ChatBotResponse;
//import com.dinhluong.dlmstore.dto.responses.ProductCardResponse;
//import com.dinhluong.dlmstore.entity.Product;
//import com.dinhluong.dlmstore.repository.ProductRepository;
//import com.fasterxml.jackson.databind.ObjectMapper;
//import lombok.RequiredArgsConstructor;
//import org.springframework.data.domain.Page;
//import org.springframework.data.domain.PageRequest;
//import org.springframework.data.domain.Pageable;
//import org.springframework.data.domain.Sort;
//import org.springframework.stereotype.Service;
//import org.springframework.util.StringUtils;
//
//import java.math.BigDecimal;
//import java.util.ArrayList;
//import java.util.HashMap;
//import java.util.List;
//import java.util.Map;
//
//
//@RequiredArgsConstructor
//public class ChatbotServiceLegacy {
//
//    private final GeminiClient geminiClient;
//    private final ProductService productService;
//    private final ProductRepository productRepository;
//    private final ObjectMapper objectMapper;
//    private final ChatbotInteractionRepository chatbotInteractionRepository;
//    public ChatBotResponse processUserMessage(Long userId,String userMessage) {
//        // 1. Phân tích Intent bằng Siêu Prompt
//        GeminiFilterCriteria criteria = extractIntentAndFilter(userMessage);
//
//        // Xử lý lỗi API hoặc parse JSON thất bại
//        if (criteria == null) {
//            return new ChatBotResponse("Hệ thống đang bận một chút, anh/chị vui lòng thử lại sau vài giây nhé.", new ArrayList<>());
//        }
//
//        // 2. Chặn câu hỏi nhảm (Out of domain)
//        if ("OUT_OF_DOMAIN".equalsIgnoreCase(criteria.getIntent())) {
//            return new ChatBotResponse("Dạ, em là trợ lý tư vấn điện thoại của DLM Store. Em chỉ hỗ trợ các câu hỏi về mua sắm thiết bị, cấu hình, giá cả thôi ạ. Anh/chị đang cần tìm điện thoại theo nhu cầu nào ạ?", new ArrayList<>());
//        }
//
//        // 3. Xử lý Chat giao tiếp (Chào hỏi, cảm ơn)
//        if ("CHAT".equalsIgnoreCase(criteria.getIntent())) {
//            String chatReply = geminiClient.callGemini("Đóng vai nhân viên tư vấn DLM Store nhiệt tình, xưng em, gọi khách là anh/chị. Hãy trả lời thân thiện và ngắn gọn với câu sau: " + userMessage);
//            return new ChatBotResponse(chatReply, new ArrayList<>());
//        }
//
//        List<ProductAiDto> contextForAi = new ArrayList<>();
//        List<ChatProductDto> fullListUi = new ArrayList<>();
//        List<ProductCardResponse> rawProductList = new ArrayList<>();
//
//        String systemFallbackNote = ""; // Lời nhắc đặc biệt cho AI nếu xài Fallback
//
//        try {
//            // Mở rộng phễu tìm kiếm: Lấy 15 sản phẩm để AI có nhiều lựa chọn
//            Pageable pageable = PageRequest.of(0, 15, Sort.by(Sort.Direction.DESC, "displayPrice"));
//
//            // ================== LOGIC TÌM KIẾM CHÍNH ==================
//            // CASE 1: TÌM CHI TIẾT 1 MÁY
//            if ("DETAIL".equalsIgnoreCase(criteria.getIntent()) && criteria.getTargetProductName() != null) {
//                Page<ProductCardResponse> page = productService.getAllProducts(
//                        null, null, null, null, null, null, null, null, null, null, null, null, null, null,
//                        criteria.getTargetProductName(), pageable);
//                if (page.hasContent()) rawProductList.addAll(page.getContent());
//            }
//            // CASE 2: SO SÁNH NHIỀU MÁY (Duyệt mảng compareProductNames)
//            else if ("COMPARE".equalsIgnoreCase(criteria.getIntent()) && criteria.getCompareProductNames() != null && !criteria.getCompareProductNames().isEmpty()) {
//                for (String modelName : criteria.getCompareProductNames()) {
//                    if (!modelName.trim().isEmpty()) {
//                        Page<ProductCardResponse> p = productService.getAllProducts(
//                                null, null, null, null, null, null, null, null, null, null, null, null, null, null,
//                                modelName.trim(), pageable);
//                        if (p.hasContent()) rawProductList.add(p.getContent().get(0));
//                    }
//                }
//            }
//            // CASE 3: SEARCH THEO BỘ LỌC
//            else {
//                List<String> rams = StringUtils.hasText(criteria.getRam()) ? List.of(criteria.getRam()) : null;
//                List<String> roms = StringUtils.hasText(criteria.getRom()) ? List.of(criteria.getRom()) : null;
//
//                // Gọi hàm Search với đầy đủ tham số từ DTO (Có minScreenSize)
//                Page<ProductCardResponse> page = productService.getAllProducts(
//                        null,
//                        criteria.getBrands(),
//                        criteria.getOsTypes(),
//                        roms,
//                        rams,
//                        criteria.getNetworks(),
//                        criteria.getMinPrice(),
//                        criteria.getMaxPrice(),
//                        criteria.getMinBattery(),
//                        null,
//                        criteria.getMinScreenSize(),
//                        null,
//                        null,
//                        null,
//                        criteria.getSearchKeyword(),
//                        pageable);
//
//                if (page.hasContent()) {
//                    rawProductList.addAll(page.getContent());
//                } else {
//                    // ================== CƠ CHẾ FALLBACK (TÌM KIẾM DỰ PHÒNG) ==================
//                    // Khi khách đòi hỏi quá vô lý (Vd: iPhone 15 Pro Max giá 5 triệu), list sẽ rỗng.
//                    // Ta nới lỏng bộ lọc: Bỏ giá tiền, ROM, RAM, Pin, Màn hình, chỉ giữ lại Hãng và Keyword cơ bản.
//                    systemFallbackNote = "LƯU Ý QUAN TRỌNG: Yêu cầu của khách hàng (về giá, cấu hình) KHÔNG CÓ THỰC TẾ tại cửa hàng lúc này. Dưới đây là các máy cùng hãng hoặc gần giống nhất. Hãy KHÉO LÉO giải thích cho khách hiểu mức giá/yêu cầu đó không khả thi, và khuyên khách tham khảo các máy thực tế này!";
//
//                    Page<ProductCardResponse> fallbackPage = productService.getAllProducts(
//                            null,
//                            criteria.getBrands(),
//                            null, null, null, null, null, null, null, null, null, null, null, null,
//                            (criteria.getSearchKeyword() != null ? criteria.getSearchKeyword() : criteria.getTargetProductName()),
//                            pageable);
//
//                    if (fallbackPage.hasContent()) {
//                        rawProductList.addAll(fallbackPage.getContent());
//                    }
//                }
//            }
//
//            // ================== MAP RA DTO CHO AI VÀ UI ==================
//            if (!rawProductList.isEmpty()) {
//                for (ProductCardResponse card : rawProductList) {
//                    if (contextForAi.stream().anyMatch(p -> p.getId().equals(card.getId()))) continue;
//
//                    productRepository.findById(card.getId()).ifPresent(p -> {
//                        contextForAi.add(toAiDto(p));
//
//                        // Tính nhãn giảm giá
//                        String discountLabel = null;
//                        if (p.getOriginalPrice() != null && p.getOriginalPrice().compareTo(p.getDisplayPrice()) > 0) {
//                            BigDecimal diff = p.getOriginalPrice().subtract(p.getDisplayPrice());
//                            discountLabel = "-" + String.format("%,.0f", diff) + "đ";
//                        }
//
//                        fullListUi.add(ChatProductDto.builder()
//                                .id(p.getId())
//                                .name(p.getName())
//                                .slug(p.getSlug())
//                                .image(p.getThumbnailUrl())
//                                .price(p.getDisplayPrice())
//                                .originalPrice(p.getOriginalPrice())
//                                .discountLabel(discountLabel)
//                                .configSummary(p.getVariants().isEmpty() ? "" : p.getVariants().get(0).getRom())
//                                .build());
//                    });
//                }
//            }
//
//        } catch (Exception e) {
//            e.printStackTrace();
//        }
//
//        // 4. Gọi AI tạo câu trả lời
//        AiFilterResponse aiResult = generateSmartResponse(userMessage, contextForAi, criteria.getIntent(), systemFallbackNote);
//
//        // 5. Lọc danh sách hiển thị trên giao diện (UI) theo ID mà AI chọn
//        List<ChatProductDto> finalUiList = new ArrayList<>();
//
//        // KIỂM TRA NULL AN TOÀN TẠI ĐÂY
//        if (aiResult != null && aiResult.getProductIds() != null && !aiResult.getProductIds().isEmpty()) {
//            for (ChatProductDto item : fullListUi) {
//                if (aiResult.getProductIds().contains(item.getId())) {
//                    finalUiList.add(item);
//                }
//            }
//        } else {
//            // Nếu AI bị sập (503) hoặc không chọn được ID, ta lấy luôn danh sách từ Database gửi ra UI
//            finalUiList.addAll(fullListUi);
//        }
//
//        // Giới hạn hiển thị UI tùy theo intent
//        int limit = "SEARCH".equalsIgnoreCase(criteria.getIntent()) ? 5 : 2;
//        if (finalUiList.size() > limit) finalUiList = finalUiList.subList(0, limit);
//        try {
//            ChatbotInteraction interaction = ChatbotInteraction.builder()
//                    .userId(userId) // Có thể null nếu khách chưa login
//                    .query(userMessage)
//                    .response(aiResult != null ? aiResult.getAnswer() : "No response")
//                    .build();
//            chatbotInteractionRepository.save(interaction);
//        } catch (Exception e) {
//            System.err.println("Lỗi lưu lịch sử Chat: " + e.getMessage());
//            // Không ném lỗi ra ngoài để tránh làm gián đoạn trải nghiệm người dùng
//        }
//        // Lúc này aiResult chắc chắn không bao giờ null nhờ fix ở Chỗ 1
//        return ChatBotResponse.builder()
//                .answer(aiResult != null ? aiResult.getAnswer() : "Hệ thống đang tải, anh/chị xem danh sách bên dưới nhé.")
//                .products(finalUiList)
//                .build();
//    }
//
//    private AiFilterResponse generateSmartResponse(String userMessage, List<ProductAiDto> products, String intent, String systemFallbackNote) {
//        if (products.isEmpty()) {
//            return new AiFilterResponse("Dạ hiện tại bên em đang tạm hết các mẫu khớp 100% với yêu cầu của mình rồi ạ. Anh/chị có muốn tham khảo sang dòng máy hoặc tầm giá khác không ạ?", new ArrayList<>());
//        }
//
//        try {
//            String jsonContext = objectMapper.writeValueAsString(products);
//            String styleInstruction;
//
//            if ("SEARCH".equalsIgnoreCase(intent)) {
//                styleInstruction = """
//                        - Đây là câu hỏi TÌM KIẾM DANH SÁCH.
//                        - Trả lời NGẮN GỌN (tối đa 3-4 dòng).
//                        - Chỉ tổng kết chung, không liệt kê chi tiết từng máy vì hệ thống sẽ tự hiển thị thẻ sản phẩm.
//                        """;
//            } else if ("DETAIL".equalsIgnoreCase(intent)) {
//                styleInstruction = """
//                        - Đây là tư vấn CHI TIẾT MỘT SẢN PHẨM.
//                        - Hãy làm nổi bật ưu điểm (Chơi game tốt không? Pin thế nào?).
//                        - Báo cho khách biết tình trạng kho (các màu đang còn hàng) và khuyến mãi nếu có.
//                        """;
//            } else {
//                styleInstruction = """
//                        - Đây là yêu cầu SO SÁNH.
//                        - BẮT BUỘC TẠO BẢNG MARKDOWN so sánh ngắn gọn các thông số chính giữa các máy.
//                        - Chốt lại khuyên khách nên chọn máy nào phù hợp với nhu cầu.
//                        """;
//            }
//
//            String prompt = """
//                    Bạn là trợ lý AI chuyên nghiệp của cửa hàng điện thoại DLM Store.
//                    Khách hỏi: "%s"
//
//                    %s
//
//                    Dữ liệu Kho Hàng thực tế hiện có (JSON):
//                    ```json %s ```
//
//                    NHIỆM VỤ CỦA BẠN:
//                    1. Trả lời tư vấn dựa trên JSON thực tế này theo phong cách sau:
//                    %s
//                    2. Trả về đúng định dạng JSON, mục "productIds" chứa danh sách các ID sản phẩm mà bạn đã nhắc đến.
//
//                    OUTPUT JSON BẮT BUỘC:
//                    {
//                        "answer": "Nội dung text bạn nói với khách...",
//                        "productIds": [1, 2]
//                    }
//                    """.formatted(userMessage, systemFallbackNote, jsonContext, styleInstruction);
//
//           String raw = geminiClient.callGemini(prompt);
//            AiFilterResponse parsedResult = parseJsonResult(raw, AiFilterResponse.class);
//
//            // BẮT LỖI 503 TẠI ĐÂY: Nếu API sập trả về text lỗi -> parse ra null
//            if (parsedResult == null) {
//                return new AiFilterResponse("Dạ hệ thống AI đang hơi quá tải một chút, anh/chị tham khảo tạm danh sách sản phẩm bên dưới giúp em nhé:", new ArrayList<>());
//            }
//            return parsedResult;
//
//        } catch (Exception e) {
//            return new AiFilterResponse("Dạ mời anh/chị tham khảo danh sách sản phẩm nổi bật nhất bên dưới ạ:", new ArrayList<>());
//        }
//    }
//
//    private GeminiFilterCriteria extractIntentAndFilter(String userMessage) {
//        String prompt = """
//                Bạn là HỆ THỐNG TRÍCH XUẤT NGỮ NGHĨA CỐT LÕI.
//                Bạn phải phân tích câu nói của khách và xuất ra duy nhất 1 chuỗi JSON hợp lệ. KHÔNG giải thích, KHÔNG có markdown ```json.
//
//                QUY TẮC TỐI THƯỢNG:
//                1. "intent" CHỈ ĐƯỢC PHÉP LÀ 1 TRONG 5 TỪ:
//                   - "OUT_OF_DOMAIN": Nếu hỏi các vấn đề không liên quan điện thoại (thời tiết, làm thơ, code...).
//                   - "CHAT": Lời chào, cảm ơn, khen ngợi.
//                   - "DETAIL": Hỏi thông tin, giá của MỘT máy duy nhất.
//                   - "COMPARE": Yêu cầu so sánh 2 máy trở lên (Đưa tên máy vào mảng 'compareProductNames').
//                   - "SEARCH": Tìm kiếm theo nhu cầu, giá, hoặc 1 dòng máy.
//
//                2. QUY TẮC XỬ LÝ GIÁ TIỀN & ĐƠN VỊ:
//                   - Nếu nhắc con số nhỏ (dưới 100) khi hỏi giá, tự động nhân với 1 triệu (VD: "dưới 15", "8 9 củ" -> 15000000, 9000000).
//                   - CƠ CHẾ BIÊN ĐỘ GIÁ (+/- 15%%): Nếu có từ ước lượng "khoảng", "tầm", "loanh quanh", "cỡ" (VD: "khoảng 10 triệu"). BẮT BUỘC trừ đi 15%% làm minPrice và cộng 15%% làm maxPrice (VD: 10tr -> minPrice: 8500000, maxPrice: 11500000).
//                   - "dưới", "nhỏ hơn" -> chỉ gán maxPrice.
//                   - "trên", "hơn" -> chỉ gán minPrice.
//
//                3. TỰ ĐỘNG CHUẨN HÓA VÀ DỊCH TỪ KHÓA (Ghi vào 'searchKeyword'):
//                   - Xử lý phủ định: Khách nói "không lấy", "né", "trừ" hãng nào thì TUYỆT ĐỐI KHÔNG đưa hãng đó vào mảng brands.
//                   - Dịch sang Keyword chuẩn của DB:
//                     + "người già", "phím bấm", "nghe gọi" -> searchKeyword: "cuc gach"
//                     + "chơi game", "pubg", "mượt" -> searchKeyword: "choi game"
//                     + "pin trâu", "dùng lâu" -> searchKeyword: "pin trau"
//                     + "rẻ", "sinh viên" -> searchKeyword: "gia re"
//                     + "màn gập" -> searchKeyword: "gap"
//                   (Nếu khách tìm tên máy, chỉ để tên vào 'targetProductName' hoặc 'compareProductNames', KHÔNG bỏ vào 'searchKeyword').
//
//                4. CHUẨN HÓA CẤU HÌNH:
//                   - ram: Ép về chuẩn chữ hoa (VD "8 ghi", "8g" -> "8GB").
//                   - rom: (VD "256 tê", "256g" -> "256GB", "1 tê" -> "1TB").
//                   - minScreenSize: Tách số thập phân nếu nói màn hình lớn (VD "trên 6 inch" -> 6.0).
//
//                OUTPUT BẮT BUỘC KHỚP VỚI OBJECT SAU:
//                {
//                  "intent": "SEARCH",
//                  "brands": ["Apple"],
//                  "osTypes": null,
//                  "networks": null,
//                  "minPrice": 8500000,
//                  "maxPrice": 11500000,
//                  "minBattery": null,
//                  "minScreenSize": null,
//                  "searchKeyword": "choi game",
//                  "ram": "8GB",
//                  "rom": null,
//                  "targetProductName": null,
//                  "compareProductNames": []
//                }
//
//                CÂU HỎI: "%s"
//                """.formatted(userMessage);
//
//        String raw = geminiClient.callGemini(prompt);
//        System.out.println("AI Extract Filter:\\n" + raw);
//        return parseJsonResult(raw, GeminiFilterCriteria.class);
//    }
//
//    private <T> T parseJsonResult(String raw, Class<T> clazz) {
//        try {
//            String json = raw.replaceAll("```json", "").replaceAll("```", "").trim();
//            if (json.contains("{")) {
//                json = json.substring(json.indexOf("{"), json.lastIndexOf("}") + 1);
//            }
//            return objectMapper.readValue(json, clazz);
//        } catch (Exception e) {
//            System.err.println("Parse JSON Error: " + e.getMessage());
//            return null;
//        }
//    }
//
//    // --- HELPER MAPPER ---
//    private ProductAiDto toAiDto(Product product) {
//        List<String> inventory = new ArrayList<>();
//        if (product.getVariants() != null) {
//            product.getVariants().forEach(v -> inventory.add(v.getColorName() + "-" + v.getRom() + ": " + (v.getStockQuantity() != null && v.getStockQuantity() > 0 ? "Còn" : "Hết")));
//        }
//
//        Map<String, String> specs = new HashMap<>();
//        if (product.getHighlightSpecs() != null) {
//            product.getHighlightSpecs().forEach(h -> specs.put(h.getLabel(), h.getValue()));
//        }
//
//        StringBuilder promo = new StringBuilder();
//        if (product.getOriginalPrice() != null && product.getDisplayPrice() != null
//                && product.getOriginalPrice().compareTo(product.getDisplayPrice()) > 0) {
//            promo.append("Giảm ").append(product.getOriginalPrice().subtract(product.getDisplayPrice())).append("đ; ");
//        }
//        if (StringUtils.hasText(product.getInstallmentText())) promo.append(product.getInstallmentText());
//
//        return ProductAiDto.builder()
//                .id(product.getId())
//                .name(product.getName())
//                .price(product.getDisplayPrice())
//                .inventory(inventory)
//                .specs(specs)
//                .promotion(promo.toString())
//                .build();
//    }
//}