package com.dinhluong.dlmstore.chatbot.service;

import com.dinhluong.dlmstore.chatbot.dto.ChatbotSearchResult;
import com.dinhluong.dlmstore.chatbot.mapper.ChatbotProductMapper;
import com.dinhluong.dlmstore.dto.ChatProductDto;
import com.dinhluong.dlmstore.dto.GeminiFilterCriteria;
import com.dinhluong.dlmstore.dto.ProductAiDto;
import com.dinhluong.dlmstore.dto.responses.ProductCardResponse;
import com.dinhluong.dlmstore.entity.Product;
import com.dinhluong.dlmstore.repository.ProductRepository;
import com.dinhluong.dlmstore.service.ProductService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class ChatbotProductSearchService {

    private static final String FALLBACK_NOTE = """
            LƯU Ý QUAN TRỌNG:
            Danh sách sau không đáp ứng đầy đủ yêu cầu ban đầu.
            Hãy giải thích rõ điểm chưa phù hợp và chỉ
            giới thiệu đây là các lựa chọn thay thế.
            Không khẳng định chúng đáp ứng mức giá
            hoặc cấu hình khách hàng yêu cầu.
            """;

    private final ProductService productService;
    private final ProductRepository productRepository;
    private final ChatbotProductMapper productMapper;

    @Transactional(readOnly = true)
    public ChatbotSearchResult search(
            GeminiFilterCriteria criteria
    ) {
        Pageable pageable = PageRequest.of(
                0,
                15,
                Sort.by(Sort.Direction.DESC, "displayPrice")
        );

        List<ProductCardResponse> cards = new ArrayList<>();
        String fallbackNote = "";

        String intent = criteria.getIntent();

        if ("DETAIL".equalsIgnoreCase(intent)) {

            cards.addAll(
                    searchByName(
                            criteria.getTargetProductName(),
                            pageable
                    )
            );

        } else if ("COMPARE".equalsIgnoreCase(intent)) {

            List<String> names = criteria.getCompareProductNames();

            if (names != null) {
                for (String name : names) {

                    List<ProductCardResponse> matches =
                            searchByName(name, pageable);

                    if (!matches.isEmpty()) {
                        cards.add(matches.get(0));
                    }
                }
            }

        } else {

            cards.addAll(searchByCriteria(criteria, pageable));

            if (cards.isEmpty()) {

                List<ProductCardResponse> alternatives =
                        searchAlternatives(criteria, pageable);

                if (!alternatives.isEmpty()) {
                    cards.addAll(alternatives);
                    fallbackNote = FALLBACK_NOTE;
                }
            }
        }

        // Loại sản phẩm trùng ID, giữ nguyên thứ tự.
        Map<Long, ProductCardResponse> unique =
                new LinkedHashMap<>();

        for (ProductCardResponse card : cards) {
            unique.putIfAbsent(card.getId(), card);
        }

        List<ProductAiDto> aiProducts = new ArrayList<>();
        List<ChatProductDto> uiProducts = new ArrayList<>();

        for (Long productId : unique.keySet()) {

            Product product = productRepository
                    .findById(productId)
                    .orElse(null);

            if (product == null || product.getStatus() != com.dinhluong.dlmstore.entity.Enums.ProductStatus.ACTIVE) {
                continue;
            }

            aiProducts.add(productMapper.toAiDto(product));
            uiProducts.add(productMapper.toUiDto(product));
        }

        return new ChatbotSearchResult(
                List.copyOf(aiProducts),
                List.copyOf(uiProducts),
                fallbackNote
        );
    }

    private List<ProductCardResponse> searchByName(
            String name,
            Pageable pageable
    ) {

        if (!StringUtils.hasText(name)) {
            return List.of();
        }

        return productService.getAllProducts(
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                name.trim(),
                pageable
        ).getContent();
    }

    private List<ProductCardResponse> searchByCriteria(
            GeminiFilterCriteria criteria,
            Pageable pageable
    ) {

        List<String> rams = StringUtils.hasText(criteria.getRam())
                ? List.of(criteria.getRam())
                : null;

        List<String> roms = StringUtils.hasText(criteria.getRom())
                ? List.of(criteria.getRom())
                : null;

        Page<ProductCardResponse> page =
                productService.getAllProducts(
                        criteria.getCategorySlug(),
                        criteria.getBrands(),
                        criteria.getOsTypes(),
                        roms,
                        rams,
                        criteria.getNetworks(),
                        criteria.getMinPrice(),
                        criteria.getMaxPrice(),
                        criteria.getMinBattery(),
                        null,
                        criteria.getMinScreenSize(),
                        null,
                        null,
                        null,
                        criteria.getSearchKeyword(),
                        pageable
                );

        return page.getContent();
    }

    private List<ProductCardResponse> searchAlternatives(
            GeminiFilterCriteria criteria,
            Pageable pageable
    ) {

        // Bước 1: Giữ nguyên hãng và keyword,
        // nới lỏng các bộ lọc cấu hình.
        List<ProductCardResponse> matches =
                productService.getAllProducts(
                        criteria.getCategorySlug(),
                        criteria.getBrands(),
                        null,
                        null,
                        null,
                        null,
                        criteria.getMinPrice(),
                        criteria.getMaxPrice(),
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        criteria.getSearchKeyword(),
                        pageable
                ).getContent();

        if (!matches.isEmpty()) {
            return matches;
        }

        // Bước 2: Nếu vẫn rỗng, thử bỏ keyword
        // nhưng giữ hãng và giới hạn giá.
        if (StringUtils.hasText(criteria.getSearchKeyword())) {

            matches = productService.getAllProducts(
                    criteria.getCategorySlug(),
                    criteria.getBrands(),
                    null,
                    null,
                    null,
                    null,
                    criteria.getMinPrice(),
                    criteria.getMaxPrice(),
                    null,
                    null,
                    null,
                    null,
                    null,
                    null,
                    null,
                    pageable
            ).getContent();
        }

        return matches;
    }
}
