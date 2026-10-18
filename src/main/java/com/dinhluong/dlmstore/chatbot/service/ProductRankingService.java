package com.dinhluong.dlmstore.chatbot.service;

import com.dinhluong.dlmstore.chatbot.enums.RankingCriterion;
import com.dinhluong.dlmstore.dto.ProductAiDto;
import org.springframework.stereotype.Service;
import java.math.BigDecimal;
import java.util.*;
import java.util.regex.Pattern;

@Service
public class ProductRankingService {
    public record Ranking(List<Long> ids, String basis, String question) { }

    public Ranking rank(List<ProductAiDto> products, RankingCriterion requested, int limit) {
        RankingCriterion criterion = requested == null ? RankingCriterion.BALANCED : requested;
        if (criterion == RankingCriterion.CAMERA) return new Ranking(List.of(), "",
                "Em chưa có dữ liệu kiểm chứng chất lượng camera để xếp hạng. Anh/chị muốn so sánh thông số camera hay giá?");
        if ((criterion == RankingCriterion.GAMING || criterion == RankingCriterion.BALANCED)
                && products.stream().noneMatch(p -> number(p, "ram") > 0 || number(p, "refreshRate") > 0)) {
            return new Ranking(List.of(), "", "Chưa đủ thông số để xếp hạng phù hợp. Anh/chị muốn ưu tiên giá hay dung lượng pin?");
        }
        Comparator<ProductAiDto> comparator = switch (criterion) {
            case PRICE_ASC -> Comparator.comparing(ProductAiDto::getPrice, Comparator.nullsLast(BigDecimal::compareTo));
            case PRICE_DESC -> Comparator.comparing(ProductAiDto::getPrice, Comparator.nullsLast(Comparator.reverseOrder()));
            case BATTERY -> Comparator.comparingDouble((ProductAiDto p) -> number(p, "battery")).reversed();
            default -> Comparator.comparingDouble((ProductAiDto p) -> number(p, "ram")).reversed()
                    .thenComparing(Comparator.comparingDouble((ProductAiDto p) -> number(p, "refreshRate")).reversed())
                    .thenComparing(Comparator.comparingDouble((ProductAiDto p) -> number(p, "battery")).reversed());
        };
        // Stable ties preserve the actual display order, never SQL/product-ID ordering.
        List<ProductAiDto> sorted = products.stream().sorted(comparator).toList();
        Set<String> models = new HashSet<>();
        List<Long> ids = new ArrayList<>();
        for (var p : sorted) {
            String model = TopicResolutionService.normalize(p.getName()).replaceAll("\\b\\d+\\s*(gb|tb)\\b", "")
                    .replaceAll("\\s+", " ").trim();
            if (models.add(model)) ids.add(p.getId());
            if (ids.size() >= limit) break;
        }
        String basis = switch (criterion) {
            case PRICE_ASC -> "Xếp theo giá hiện tại tăng dần từ database.";
            case PRICE_DESC -> "Xếp theo giá hiện tại giảm dần từ database.";
            case BATTERY -> "Ưu tiên dung lượng pin được lưu; không suy ra thời lượng sử dụng thực tế.";
            default -> "Ưu tiên RAM cao nhất trong biến thể đang bán, sau đó tần số quét và dung lượng pin theo dữ liệu đang có. "
                    + "Đây là tiêu chí phần cứng tham khảo, không phải benchmark; chưa đủ căn cứ khẳng định FPS hoặc hiệu năng chipset.";
        };
        return new Ranking(List.copyOf(ids), basis, ids.size() < Math.min(limit, 2)
                ? "Chưa có đủ hai dòng máy khác nhau còn hợp lệ trong danh sách." : null);
    }

    private static final Pattern NUMERIC = Pattern.compile("(\\d+(?:\\.\\d+)?)");
    static double number(ProductAiDto product, String field) {
        String value = product.getSpecs() == null ? null : product.getSpecs().get(field);
        if (value == null) return -1;
        var matcher = NUMERIC.matcher(value);
        double result = -1;
        while (matcher.find()) result = Math.max(result, Double.parseDouble(matcher.group(1)));
        return result;
    }
}
