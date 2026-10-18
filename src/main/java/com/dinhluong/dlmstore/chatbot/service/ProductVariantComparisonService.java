package com.dinhluong.dlmstore.chatbot.service;

import com.dinhluong.dlmstore.chatbot.dto.VariantRequest;
import com.dinhluong.dlmstore.chatbot.mapper.ChatbotProductMapper;
import com.dinhluong.dlmstore.dto.responses.ChatBotResponse;
import com.dinhluong.dlmstore.entity.Product;
import com.dinhluong.dlmstore.entity.ProductVariant;
import com.dinhluong.dlmstore.entity.Enums.ProductStatus;
import com.dinhluong.dlmstore.repository.ProductRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.*;

@Service
@RequiredArgsConstructor
public class ProductVariantComparisonService {
    private final ProductRepository products;
    private final ChatbotCatalogService catalog;
    private final ChatbotProductMapper mapper;
    public record Result(ChatBotResponse response, List<Map<String, Object>> references, String question) { }

    @Transactional(readOnly = true)
    public Result compare(List<VariantRequest> requests) {
        List<Map<String, Object>> refs = new ArrayList<>();
        for (var request : requests) {
            var named = catalog.byNames(List.of(request.productName()));
            if (named.question() != null) return new Result(null, List.of(), named.question());
            var p = products.findById(named.products().uiProducts().get(0).getId()).orElseThrow();
            var matches = p.getVariants() == null ? List.<ProductVariant>of() : p.getVariants().stream()
                    .filter(ProductVariant::isActive).filter(v -> matches(request.ram(), v.getRam())
                            && matches(request.rom(), v.getRom()) && matches(request.color(), v.getColorName())).toList();
            if (matches.size() != 1) return new Result(null, List.of(),
                    "Cấu hình có nhiều biến thể hoặc không còn được bán. Anh/chị cho biết RAM, ROM và màu cụ thể nhé?");
            refs.add(Map.of("productId", p.getId(), "variantId", matches.get(0).getId()));
        }
        return reload(refs);
    }

    @Transactional(readOnly = true)
    public Result reload(List<Map<String, Object>> references) {
        StringBuilder table = new StringBuilder("| Model | RAM | ROM | Màu | Giá biến thể | Tồn kho |\n|---|---|---|---|---:|---:|\n");
        Map<Long, Product> displayed = new LinkedHashMap<>();
        Set<Long> variants = new HashSet<>();
        List<Map<String,Object>> currentReferences = new ArrayList<>();
        for (var ref : references) {
            long productId = ((Number)ref.get("productId")).longValue(), variantId = ((Number)ref.get("variantId")).longValue();
            var p = products.findById(productId).filter(product -> product.getStatus() == ProductStatus.ACTIVE).orElse(null);
            var v = p == null || p.getVariants() == null ? null : p.getVariants().stream()
                    .filter(variant -> variant.isActive() && variant.getId() == variantId).findFirst().orElse(null);
            if (v == null || !variants.add(variantId)) return new Result(null, List.of(),
                    "Biến thể không còn hợp lệ hoặc bị chọn trùng. Anh/chị chọn lại cấu hình giúp em nhé?");
            displayed.put(p.getId(), p);
            Map<String,Object> updated = new LinkedHashMap<>();
            updated.put("productId",p.getId());updated.put("variantId",v.getId());updated.put("version",v.getVersion());
            currentReferences.add(updated);
            table.append("| ").append(cell(p.getName())).append(" | ").append(cell(v.getRam())).append(" | ")
                    .append(cell(v.getRom())).append(" | ").append(cell(v.getColorName())).append(" | ")
                    .append(v.getPrice() == null ? "Chưa có dữ liệu" : v.getPrice().toPlainString() + " đ")
                    .append(" | ").append(v.getStockQuantity() == null ? "Chưa có dữ liệu" : v.getStockQuantity()).append(" |\n");
        }
        return new Result(new ChatBotResponse(table.toString(), displayed.values().stream().map(mapper::toUiDto).toList()),
                List.copyOf(currentReferences), null);
    }
    private static boolean matches(String requested, String actual) {
        return requested == null || requested.isBlank() || requested.equalsIgnoreCase(actual);
    }
    private static String cell(String text) {
        return text == null ? "Chưa có dữ liệu" : text.replace("|", "\\|").replace("\n", " ").replace("<", "&lt;");
    }
}
