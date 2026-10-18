# Kiểm thử chatbot

## Chế độ đang được phép

Người dùng chọn **không gọi AI thật**. Đợt triển khai này chỉ chạy unit, HTTP/integration và deterministic evaluation. Không có request tới provider thật.

```powershell
.\mvnw.cmd clean verify --no-transfer-progress
```

Dataset: `src/test/resources/chatbot/evaluation-scenarios.json`.

- 40 scenario tiếng Việt.
- 26 scenario đa lượt.
- 74 lượt tổng cộng.
- Fixtures sản phẩm/user tự tạo, không phải dữ liệu production.
- Test DB bắt buộc in-memory bằng DynamicPropertySource; không để environment đổi sang DB ngoài.
- Provider output giả lập; AI router/schema validator/backend execution chạy thật.

Kết quả expected/actual nằm ở `target/chatbot-evaluation/deterministic-results.json`. Mỗi scenario ghi input, expected action/state constraints, actual intent/topic/message action, actual context, actual HTTP response, PASS/FAIL và lý do thất bại. Không lưu JWT, API key hay cookie token.

PASS ở đây chứng minh backend thực thi đúng khi nhận action có cấu trúc. Nó không đo độ chính xác hiểu tiếng Việt của Gemini/OpenAI/Ollama.

`docs/chatbot/live-evaluation-status.json` ghi 40 scenario live chưa chạy và lý do; các trường actual đều null.

## Runner live dành cho một lần được cấp phép sau này

`ChatbotLiveEvaluationIT` không nằm trong test pattern mặc định, có environment gate và budget bắt buộc. **Không chạy các lệnh dưới đây khi chưa được phép phát sinh request AI.**

Ví dụ cấu hình một lần chạy đã được duyệt:

```powershell
$env:DLMSTORE_RUN_LIVE_EVAL = 'true'
$env:DLMSTORE_LIVE_EVAL_MAX_CALLS = '20'
$env:DLMSTORE_LIVE_EVAL_MODEL = 'OLLAMA'
.\mvnw.cmd '-Dtest=ChatbotLiveEvaluationIT' test
```

Chọn explicit `GEMINI`, `OPENAI` hoặc `OLLAMA`; không dùng AUTO trong runner để giữ budget dễ kiểm soát. Cấu hình model/endpoint/credential bằng cơ chế hiện có, không ghi key vào dataset, command line hoặc report.

Budget 1–100 là upper bound số provider request được cho phép trong lần chạy. Gemini bị tính bảo thủ 2 đơn vị mỗi lần generate vì có thể thử tối đa hai key; OpenAI/Ollama 1. Hết budget thì dừng/skip phần còn lại, không tự tăng budget. Với Gemini, chạy đủ 74 lượt có thể cần kế hoạch nhiều đợt được duyệt; runner không hứa hoàn tất mọi scenario trong một budget nhỏ.

Kết quả live xuất `target/chatbot-evaluation/live-results.json`, gồm model, charged upper bound, budget, response, action và state thực tế. Check sơ bộ đúng intent/số sản phẩm/clarification chưa đủ để coi là full PASS: trạng thái `DETERMINISTIC_CHECKS_PASSED_REVIEW_REQUIRED` yêu cầu review topic/filter/reference và facts theo rubric dưới đây.

## Rubric live

1. Intent/action phản ánh đúng yêu cầu, không đổi RECALL/reference thành fresh SEARCH.
2. Topic đúng; small talk/out-of-domain không làm mất chủ đề hoặc cặp sản phẩm.
3. IDs thuộc candidates có quyền; vị trí khớp thứ tự card từng hiển thị.
4. Filter merge/replace/clear đúng; không trộn điều kiện topic khác.
5. Giá, thông số, stock và variant có dữ liệu DB tương ứng; không có benchmark giả.
6. Ranking nói rõ tiêu chí/thiếu dữ liệu; không khẳng định chất lượng camera chỉ từ MP.
7. Clarification giữ action/candidate set gốc và không lặp vô hạn.
8. Response giữ schema và không lộ lịch sử/credential phiên khác.

Các mục 1–8 ưu tiên assertion xác định trên history/state/product data. Phần văn phong chỉ review ngữ nghĩa/tính phù hợp, không exact-match cả câu và không tự ghi tỷ lệ accuracy 100% khi chưa đánh giá.

## MySQL và browser

Docker daemon không hoạt động trong phiên này. H2 PASS không thay thế MySQL 8 test riêng cho JSON/EAV/index/locking. Script index dưới `src/main/resources/db/manual` chưa được áp dụng.

Suite CORS giữ 28 regression test, gồm real HTTP/Tomcat. Browser thật và React renderer chưa được kiểm tra; cặp frontend HTTPS/backend HTTP còn chịu chính sách SameSite của cookie hiện có.
