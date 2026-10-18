# Chatbot clarification regression fix

Ngày kiểm chứng: 2026-10-19. Phạm vi: pending clarification, topic resolution, product disambiguation và request selection liên quan. Không thay đổi CORS, guest token, JWT hoặc API nghiệp vụ khác trong đợt sửa này.

## Root cause

1. `ChatbotBusinessService.resumePending` chỉ tiếp tục khi AI trả `ANSWER_CLARIFICATION`, đồng thời khôi phục cả `contextAction=NEW_TOPIC` của yêu cầu đầu tiên. `TopicResolutionService.resolve` vì vậy tạo topic mới. `ConversationTurnStore.commit` tìm pending theo topic vừa chọn nên bỏ qua pending cũ.
2. `ChatbotCatalogService.byNames` dừng ở operand mơ hồ đầu tiên, trả các sản phẩm đã chọn thay vì các ứng viên cần phân biệt. Operand so sánh phía sau chưa được resolve. Pending không có tên/ID ứng viên đúng; reply ngắn phụ thuộc hoàn toàn vào AI. Việc ghi đè `compareProductNames` bằng reply cũng có thể mất sản phẩm đối chiếu.
3. `ConversationTurnStore` trước đây chỉ ghi `resolvedValue.message`; thiếu dữ liệu để kiểm tra original action, sản phẩm và bộ lọc sau resume.

Test `PendingClarificationTest.pendingAnswerCannotCreateAnotherTopicEvenWhenOriginalActionWasNewTopic` được chạy trước khi sửa topic resolver và FAIL: `expected false but was true`. Bằng chứng nằm trong `chatbot-critical-reproduce.log`. Các regression cho ứng viên/Copy được bổ sung và chạy sau khi sửa; không tuyên bố đã chạy chúng trên code cũ. Không truy cập hoặc sửa các conversation ID thực tế trong yêu cầu của người dùng.

## Thay đổi

- Sau ownership check và load snapshot, orchestrator thử xử lý pending trước inference. Ngân sách/lựa chọn hợp lệ không cần gọi AI; vẫn tiếp tục original intent qua business pipeline và atomic commit.
- `PendingAnswerParser` nhận diện ngân sách triệu/củ và khoảng giá, phân loại câu trả lời/yêu cầu mới/hủy/không liên quan. Prefix yêu cầu mới rõ ràng được ưu tiên trước dữ liệu ngân sách.
- Topic resolver ràng buộc câu trả lời pending vào topic được sở hữu của pending; resume chuyển context action về `CONTINUE`.
- Name lookup đọc cả các operand, giữ counterpart đã resolve và lưu ứng viên mơ hồ riêng. Truy vấn sắp xếp ID ổn định; exact normalized name được ưu tiên. Tên trùng được hiển thị kèm ID và giá.
- Matching trong tập ứng viên: structured ID hợp lệ; exact name; alias duy nhất; fuzzy edit distance tối đa 0.12 và cách ứng viên tiếp theo ít nhất 0.08; trường hợp không đủ rõ giữ câu hỏi hiện tại. Copy là phần có nghĩa của tên. Lựa chọn vị trí dùng thứ tự đã lưu.
- Request hỗ trợ thêm `selectedProductId` và `clarificationId` tùy chọn. Phải gửi cả hai, đúng pending hiện hành và đúng candidate set. Ownership được kiểm tra trước. ID lạ/stale trả 400; hai field không ảnh hưởng client cũ. Trường ID nội bộ trên criteria có `JsonIgnore`, không tin ID do AI cung cấp.
- JSON pending lưu original criteria/intent, unresolved slot, candidate IDs/names và resolved counterpart. Entity hiện có lưu conversation/topic, status, attempts và expiry; không cần migration schema. Pending cũ thiếu metadata được nâng cấp dưới session lock bằng original criteria trước khi nhận lựa chọn.
- Commit kiểm tra revision/context/pending hiện hành trước resolve. Lưu resolved criteria, action và returned product IDs; cập nhật history/lastComparedProductIds cùng transaction. Explicit new request hủy pending cũ kể cả khi đổi topic. Không resolve lần hai.
- Nếu đã nhận ngân sách nhưng vẫn còn hơn 50 ứng viên, hỏi thêm hãng/cấu hình thay vì hỏi lại ngân sách. Pending ngân sách được đóng trước khi tạo câu hỏi cho bước tiếp theo trong cùng topic.

Ví dụ request lựa chọn có cấu trúc (ID minh họa, lấy từ pending thực tế):

```json
{
  "conversationId": "<owned conversation UUID>",
  "message": "Chọn sản phẩm này",
  "clarificationId": 123,
  "selectedProductId": 456
}
```

## Kết quả kiểm thử

Lệnh: `mvnw.cmd clean verify --no-transfer-progress`.

**BUILD SUCCESS; 166 tests, 0 failures, 0 errors, 0 skipped trong default suite.** Log: `chatbot-critical-verify.log`. Có 32 lượt test bổ sung so với 134 test của đợt trước. Maven đã đóng gói Spring Boot JAR.

| Nhóm | Bằng chứng |
|---|---|
| 4 cách trả lời ngân sách | Chạy mỗi cách với RECOMMEND, SEARCH, RANK; giữ topic, pending RESOLVED, đúng maxPrice, có cards/history/action |
| Exact name, Copy, full compare sentence, typo, vị trí, structured ID | `namedChoiceResumesCompareAndPreservesCounterpart`; giữ counterpart và original COMPARE, không tạo topic mới |
| Pending cũ thiếu metadata/IDs sai | Cùng test với case `legacy`; nâng cấp từ original criteria rồi resolve đúng sản phẩm |
| Tên trùng | `identicalNamesRequireDiscriminableChoiceAndStructuredIdResumesDetail`; không tự chọn theo thứ tự, ID chọn đúng DETAIL |
| Invalid, unrelated, cancel, new request, expired, outside/stale ID | `pendingLifecycleGuards`; kiểm tra status, topic count và pending ID |
| Matching và topic invariant | `PendingClarificationTest`, gồm ambiguous ties và vị trí ngoài phạm vi |
| HTTP thực, multi-turn | `ClarificationHttpTest.realHttpCompareChoiceAndRecallKeepTopicAndPersistOneResolution`, embedded Tomcat cổng ngẫu nhiên, dữ liệu H2 riêng |
| Concurrent resolution | Hai HTTP request structured selection đồng thời: một 200, một stale 400; một topic, một resolution, đúng hai history rows |
| AI failure khi resume | Valid budget/name answer vẫn thành công khi mock router ném lỗi offline |
| Ownership, JWT/guest, expiry, dedup, stale plan/price, atomic persistence | Bộ integration/security có sẵn chạy lại và PASS |
| Evaluation deterministic | 40 scenario có sẵn PASS; provider được mock, không phải live AI |
| CORS regression | 28 test có sẵn PASS; không sửa cấu hình CORS trong đợt này |

## Phần chưa kiểm chứng ngoài môi trường test

- **MySQL persistence: NOT RUN.** Kiểm tra `docker info` thất bại vì pipe `dockerDesktopLinuxEngine` không tồn tại. Các test persistence ở trên chạy H2 isolated, không thay thế việc xác nhận MySQL JSON/generated unique pending index trên một database test riêng. Không kết nối database ứng dụng để seed/reset dữ liệu.
- **Live AI: NOT RUN theo lựa chọn “Chưa gọi AI thật” của người dùng.** Không tính test mock/offline thành live PASS. Hai scenario tiếng Việt trong yêu cầu chưa được chạy với provider thật.
- Chưa xác nhận lại trực tiếp hai conversation thực tế trên server đang chạy. Bản sửa cần được khởi động/deploy vào backend để frontend sử dụng code mới.
