package com.dinhluong.dlmstore.chatbot.service;

import com.dinhluong.dlmstore.chatbot.dto.ChatbotSearchResult;
import com.dinhluong.dlmstore.dto.ProductAiDto;
import com.dinhluong.dlmstore.dto.responses.ChatBotResponse;
import org.springframework.stereotype.Service;
import java.util.*;

/** Factual product responses are rendered from database values; an LLM cannot invent a price/specification. */
@Service
public class GroundedProductResponseService {
    public ChatBotResponse render(String intent, ChatbotSearchResult products, String basis) {
        if (products.aiProducts().isEmpty()) return new ChatBotResponse(
                "Chưa có sản phẩm còn được bán phù hợp với yêu cầu. Anh/chị có thể đổi ngân sách hoặc bộ lọc.", List.of());
        StringBuilder answer = new StringBuilder();
        if (!products.fallbackNote().isBlank()) answer.append("Các máy dưới đây là lựa chọn thay thế, chưa đáp ứng đầy đủ điều kiện.\n\n");
        if (basis != null && !basis.isBlank()) answer.append(basis).append("\n\n");
        if (products.truncated()) answer.append("Đang hiển thị một trang kết quả; anh/chị có thể yêu cầu trang tiếp theo.\n\n");
        answer.append("| Sản phẩm | Giá hiện tại | RAM / ROM | Màn hình | Pin | Chip | Camera | Tình trạng biến thể |\n")
                .append("|---|---:|---|---|---|---|---|---|\n");
        for (var p : products.aiProducts()) {
            Map<String, String> specs = p.getSpecs() == null ? Map.of() : p.getSpecs();
            answer.append("| ").append(cell(p.getName())).append(" | ")
                    .append(p.getPrice() == null ? "Chưa có dữ liệu" : p.getPrice().toPlainString() + " đ")
                    .append(" | ").append(cell(specs.get("ram"))).append(" / ").append(cell(specs.get("rom")))
                    .append(" | ").append(cell(specs.get("screen"))).append(" | ").append(cell(specs.get("battery")))
                    .append(" | ").append(cell(specs.get("chip"))).append(" | ").append(cell(specs.get("camera")))
                    .append(" | ").append(cell(p.getInventory() == null ? null : String.join(", ", p.getInventory()))).append(" |\n");
        }
        if ("COMPARE".equals(intent) && products.aiProducts().size() >= 2) {
            var priced = products.aiProducts().stream().filter(p -> p.getPrice() != null)
                    .min(Comparator.comparing(ProductAiDto::getPrice));
            priced.ifPresent(p -> {
                answer.append("\nTrong các máy có dữ liệu giá, ").append(cell(p.getName()))
                        .append(" có giá niêm yết thấp nhất hiện tại: ").append(p.getPrice().toPlainString()).append(" đ.\n");
                for (var other : products.aiProducts()) {
                    if (other.getPrice() != null && other.getPrice().compareTo(p.getPrice()) > 0) {
                        answer.append("- Đánh đổi về giá của ").append(cell(other.getName())).append(": cao hơn ")
                                .append(cell(p.getName())).append(" ")
                                .append(other.getPrice().subtract(p.getPrice()).toPlainString()).append(" đ.\n");
                    }
                }
            });
            for (String field : List.of("ram", "battery", "refreshRate")) {
                var known = products.aiProducts().stream().filter(p -> ProductRankingService.number(p, field) > 0).toList();
                if (known.size() < 2) continue;
                var max = known.stream().max(Comparator.comparingDouble(p -> ProductRankingService.number(p, field))).orElseThrow();
                double min = known.stream().mapToDouble(p -> ProductRankingService.number(p, field)).min().orElseThrow();
                if (ProductRankingService.number(max, field) > min) answer.append("- ")
                        .append(cell(max.getName())).append(" có ").append(field).append(" lớn hơn theo dữ liệu đang có: ")
                        .append(cell(max.getSpecs().get(field))).append("; không suy ra benchmark hoặc thời lượng thực tế.\n");
            }
        }
        answer.append("\nĐiểm có thể đối chiếu: các thông số trong bảng. Hạn chế: trường chưa có dữ liệu không được suy đoán; "
                + "không có benchmark/FPS hay đánh giá chất lượng camera đã kiểm chứng. Giá và tồn kho có thể thay đổi.");
        return new ChatBotResponse(answer.toString(), products.uiProducts());
    }

    private static String cell(String text) {
        return text == null || text.isBlank() ? "Chưa có dữ liệu" : text.replace("|", "\\|")
                .replace("\n", " ").replace("\r", " ").replace("<", "&lt;").replace(">", "&gt;")
                .replace("[", "\\[").replace("]", "\\]").replace("`", "\\`");
    }
}
