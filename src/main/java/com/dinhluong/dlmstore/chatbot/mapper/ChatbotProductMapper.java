package com.dinhluong.dlmstore.chatbot.mapper;

import com.dinhluong.dlmstore.dto.ChatProductDto;
import com.dinhluong.dlmstore.dto.ProductAiDto;
import com.dinhluong.dlmstore.entity.Product;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Component
public class ChatbotProductMapper {

    public ProductAiDto toAiDto(Product product) {

        List<String> inventory = new ArrayList<>();

        if (product.getVariants() != null) {
            product.getVariants().forEach(variant -> {
                if (!variant.isActive()) return;

                boolean inStock =
                        variant.getStockQuantity() != null
                                && variant.getStockQuantity() > 0;

                inventory.add(
                        variant.getColorName() + "-"
                                + variant.getRom() + ": "
                                + (inStock ? "Còn" : "Hết")
                );
            });
        }

        Map<String, String> specs = new HashMap<>();
        if (product.getBrand() != null) specs.put("brand", product.getBrand().getName());
        if (product.getAvailableRams() != null) specs.put("ram", String.join(", ", product.getAvailableRams()));
        if (product.getAvailableRoms() != null) specs.put("rom", String.join(", ", product.getAvailableRoms()));
        if (product.getVariants() != null && !product.getVariants().isEmpty()) {
            specs.put("ram", product.getVariants().stream().filter(com.dinhluong.dlmstore.entity.ProductVariant::isActive)
                    .map(com.dinhluong.dlmstore.entity.ProductVariant::getRam).filter(java.util.Objects::nonNull).distinct()
                    .collect(java.util.stream.Collectors.joining(", ")));
            specs.put("rom", product.getVariants().stream().filter(com.dinhluong.dlmstore.entity.ProductVariant::isActive)
                    .map(com.dinhluong.dlmstore.entity.ProductVariant::getRom).filter(java.util.Objects::nonNull).distinct()
                    .collect(java.util.stream.Collectors.joining(", ")));
        }
        if (product.getBatteryCapacity() != null) specs.put("battery", product.getBatteryCapacity() + " mAh");
        if (product.getRefreshRate() != null) specs.put("refreshRate", product.getRefreshRate() + " Hz");
        if (product.getScreenSize() != null) specs.put("screen", product.getScreenSize() + " inch"
                + (product.getRefreshRate() == null ? "" : ", " + product.getRefreshRate() + " Hz"));
        if (product.getSpecValues() != null) product.getSpecValues().forEach(spec -> {
            if (spec.getAttribute() != null && spec.getValue() != null) {
                String name = spec.getAttribute().getName().toLowerCase(java.util.Locale.ROOT);
                specs.put(spec.getAttribute().getName(), spec.getValue());
                if (name.contains("chip") || name.contains("processor")) specs.put("chip", spec.getValue());
                if (name.contains("camera")) specs.putIfAbsent("camera", spec.getValue());
            }
        });

        if (product.getHighlightSpecs() != null) {
            product.getHighlightSpecs().forEach(spec ->
                    specs.put(spec.getLabel(), spec.getValue())
            );
        }

        StringBuilder promotion = new StringBuilder();

        if (product.getOriginalPrice() != null
                && product.getDisplayPrice() != null
                && product.getOriginalPrice()
                .compareTo(product.getDisplayPrice()) > 0) {

            BigDecimal discount = product.getOriginalPrice()
                    .subtract(product.getDisplayPrice());

            promotion.append("Giảm ")
                    .append(discount)
                    .append("đ; ");
        }

        if (StringUtils.hasText(product.getInstallmentText())) {
            promotion.append(product.getInstallmentText());
        }

        return ProductAiDto.builder()
                .id(product.getId())
                .name(product.getName())
                .price(product.getDisplayPrice())
                .inventory(inventory)
                .specs(specs)
                .promotion(promotion.toString())
                .build();
    }

    public ChatProductDto toUiDto(Product product) {

        String discountLabel = null;

        if (product.getOriginalPrice() != null
                && product.getDisplayPrice() != null
                && product.getOriginalPrice()
                .compareTo(product.getDisplayPrice()) > 0) {

            BigDecimal discount = product.getOriginalPrice()
                    .subtract(product.getDisplayPrice());

            discountLabel = "-"
                    + String.format("%,.0f", discount)
                    + "đ";
        }

        String configSummary = "";

        if (product.getVariants() != null
                && !product.getVariants().isEmpty()) {

            configSummary = product.getVariants().stream().filter(com.dinhluong.dlmstore.entity.ProductVariant::isActive)
                    .map(com.dinhluong.dlmstore.entity.ProductVariant::getRom).filter(java.util.Objects::nonNull)
                    .findFirst().orElse("");
        }

        return ChatProductDto.builder()
                .id(product.getId())
                .name(product.getName())
                .slug(product.getSlug())
                .image(product.getThumbnailUrl())
                .price(product.getDisplayPrice())
                .originalPrice(product.getOriginalPrice())
                .discountLabel(discountLabel)
                .configSummary(configSummary)
                .build();
    }
}
