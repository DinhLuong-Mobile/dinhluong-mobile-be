package com.dinhluong.dlmstore.chatbot.prompt;

import org.springframework.stereotype.Component;

@Component
public class IntentPromptBuilder {

    public String build(String userMessage) {

        return """
                Bạn là HỆ THỐNG TRÍCH XUẤT NGỮ NGHĨA CỐT LÕI
                cho chatbot tư vấn điện thoại DLM Store.

                Hãy phân tích câu nói của khách hàng và
                trả về DUY NHẤT một JSON hợp lệ.

                Không giải thích.
                Không markdown.
                Không thêm nội dung ngoài JSON.

                QUY TẮC PHÂN LOẠI INTENT:

                1. OUT_OF_DOMAIN:
                   Câu hỏi không liên quan đến điện thoại
                   hoặc hoạt động mua sắm thiết bị.

                2. CHAT:
                   Chào hỏi, cảm ơn, khen ngợi.

                3. DETAIL:
                   Hỏi chi tiết một điện thoại cụ thể.

                4. COMPARE:
                   So sánh từ hai điện thoại trở lên.

                5. SEARCH:
                   Tìm kiếm theo nhu cầu, giá, hãng,
                   cấu hình hoặc đặc điểm sản phẩm.

                QUY TẮC XỬ LÝ GIÁ:

                - "dưới 10 triệu" -> maxPrice: 10000000
                - "trên 15 triệu" -> minPrice: 15000000
                - "khoảng 10 triệu" -> minPrice: 8500000,
                                      maxPrice: 11500000
                - "8 củ" -> 8000000
                - "10tr" -> 10000000

                QUY TẮC RAM / ROM:

                - "8 ghi" -> "8GB"
                - "12g RAM" -> "12GB"
                - "256 ghi" -> "256GB"
                - "1 tê" -> "1TB"

                CHUẨN HÓA TỪ KHÓA:

                - "chơi game", "PUBG" -> "choi game"
                - "pin trâu" -> "pin trau"
                - "giá rẻ", "sinh viên" -> "gia re"
                - "màn gập" -> "gap"
                - "người già", "phím bấm" -> "cuc gach"

                QUY TẮC:

                - Không tự tạo thông tin không có trong câu hỏi.
                - Nếu khách loại trừ một hãng, không đưa hãng
                  đó vào danh sách brands.
                - Nếu khách hỏi một mẫu máy cụ thể,
                  dùng targetProductName.
                - Nếu khách so sánh nhiều mẫu máy,
                  dùng compareProductNames.
                - Không đưa tên mẫu máy vào searchKeyword
                  khi đã đưa vào targetProductName hoặc
                  compareProductNames.

                OUTPUT JSON:

                {
                  "intent": "SEARCH",
                  "brands": null,
                  "osTypes": null,
                  "networks": null,
                  "minPrice": null,
                  "maxPrice": null,
                  "minBattery": null,
                  "minScreenSize": null,
                  "searchKeyword": null,
                  "ram": null,
                  "rom": null,
                  "targetProductName": null,
                  "compareProductNames": []
                }

                CÂU HỎI KHÁCH HÀNG:
                %s
                """.formatted(userMessage);
    }
}