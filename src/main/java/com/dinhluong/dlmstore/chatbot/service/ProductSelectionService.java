package com.dinhluong.dlmstore.chatbot.service;

import com.dinhluong.dlmstore.dto.ChatProductDto;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class ProductSelectionService {

    public List<ChatProductDto> select(
            List<ChatProductDto> products,
            List<Long> selectedIds,
            String intent
    ) {

        if (products == null || products.isEmpty()) {
            return List.of();
        }

        List<ChatProductDto> selected = new ArrayList<>();

        if (selectedIds != null && !selectedIds.isEmpty()) {

            Map<Long, ChatProductDto> productMap =
                    products.stream()
                            .collect(Collectors.toMap(
                                    ChatProductDto::getId,
                                    Function.identity(),
                                    (first, second) -> first,
                                    LinkedHashMap::new
                            ));

            for (Long id : selectedIds) {
                ChatProductDto product = productMap.get(id);

                if (product != null && !selected.contains(product)) {
                    selected.add(product);
                }
            }
        }

        if (selected.isEmpty()) {
            selected.addAll(products);
        }

        int limit = switch (
                intent.toUpperCase(Locale.ROOT)
                ) {
            case "SEARCH" -> 5;
            case "DETAIL" -> 1;
            case "COMPARE" -> selected.size();
            default -> 0;
        };

        return selected.stream()
                .limit(limit)
                .toList();
    }
}