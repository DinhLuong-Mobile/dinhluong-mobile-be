# DLMStore — sửa CORS development

Ngày kiểm chứng: 18/10/2026, timezone Asia/Bangkok.

## Root cause đã tái hiện

Backend `http://localhost:8080` trả **403**, body `Invalid CORS request`, không có `Access-Control-Allow-Origin` khi nhận OPTIONS từ `https://localhost:5173` trên cả 5 đường dẫn được báo lỗi. Cùng request từ `http://localhost:5173` trả **200** với header CORS đầy đủ. Scheme HTTP/HTTPS khác nhau tạo thành hai origin khác nhau.

Fallback cũ trong WebConfig chỉ có `http://localhost:3000,http://localhost:5173`. Đây là thiếu sót khi thu hẹp allowlist ở đợt audit trước.

Trước khi sửa, 5 regression case HTTPS preflight cũng đều FAIL: expected 200, actual 403. Sau sửa tất cả PASS.

Kiểm tra cấu hình thực tế trong workspace:

- `WebConfig` là nơi duy nhất khai báo MVC CORS; không tìm thấy `@CrossOrigin`, CorsFilter hoặc CorsConfigurationSource khác trong source.
- `SecurityConfig` dùng cùng cấu hình CORS của Spring MVC. Đổi lời gọi configurer thủ công sang `Customizer.withDefaults()` để Spring Security đăng ký CORS theo cơ chế chuẩn.
- File `src/main/resources/application.properties` tại thời điểm kiểm tra không có khóa `app.cors.allowed-origins`; khác với mô tả có cấu hình HTTP trong yêu cầu. Đã bổ sung khóa local rõ ràng, giữ nguyên mọi cấu hình khác.
- Không có biến CORS/profile/config/JVM-options liên quan ghi đè trong môi trường shell của agent. Không khẳng định đã đọc được environment riêng của tiến trình backend/IDE khác.
- File application.properties bị gitignore và không được stage/commit vì có cấu hình riêng của máy. Fallback trong WebConfig cũng đã sửa để checkout khác vẫn có development defaults đúng.

## Phạm vi sửa

| File | Thay đổi |
|---|---|
| src/main/java/com/dinhluong/dlmstore/config/WebConfig.java | Thêm origin chính xác `https://localhost:5173` vào development defaults; giữ HTTP localhost và credentials |
| src/main/java/com/dinhluong/dlmstore/config/SecurityConfig.java | `.cors(Customizer.withDefaults())`; không sửa rule authorization, JWT filter hoặc CSRF |
| src/main/resources/application.properties | Bổ sung allowlist local đầy đủ; file riêng của máy, được gitignore |
| src/test/java/com/dinhluong/dlmstore/config/CorsRegressionTest.java | 21 test MockMvc với controller, security chain và JWT filter thật |
| src/test/java/com/dinhluong/dlmstore/config/CorsHttpIntegrationTest.java | 7 test HTTP thực qua embedded Tomcat cổng ngẫu nhiên; không khởi động database |

Không sửa service nghiệp vụ, guest ownership, hash token, cookie SameSite/HttpOnly/Secure hay cơ chế kiểm tra JWT trong đợt này. Không thêm wildcard origin hay permitAll cho API được bảo vệ.

Allowlist development:

```properties
app.cors.allowed-origins=http://localhost:3000,http://localhost:5173,https://localhost:5173
```

Deployment khác có thể thay toàn bộ danh sách bằng property hoặc `APP_CORS_ALLOWED_ORIGINS`. Khi deployment cung cấp override thì phải khai báo đầy đủ các origin thực sự được phép; fallback không tự nối thêm origin vào override.

## Kết quả test

Build: `./mvnw.cmd clean verify --no-transfer-progress`.

**BUILD SUCCESS — exit code 0 — 63 test PASS, 0 failure, 0 error, 0 skipped.**

Gồm 28 test CORS mới (21 MockMvc + 7 HTTP), 34 test chatbot có sẵn và 1 context test. Chi tiết: `target/surefire-reports/`, log `cors-build.log`. Build kết thúc lúc 23:03 ngày 18/10/2026.

| Endpoint | HTTPS preflight trước sửa trên :8080 | Preflight sau sửa qua HTTP test server | Request sau sửa |
|---|---|---|---|
| GET /api/cart/15 | 403 | 200, đúng origin và credentials | Không JWT: 401; JWT hợp lệ theo mock provider: 200 |
| GET /api/products/featured | 403 | 200, đúng origin và credentials | Public GET: 200 |
| GET /api/notifications | 403 | 200, đúng origin và credentials | Không JWT: 401; JWT hợp lệ theo mock provider: 200 |
| GET /api/notifications/unread-count | 403 | 200, đúng origin và credentials | Không JWT: 401; JWT hợp lệ theo mock provider: 200 |
| POST /api/chatbot | 403 | 200, đúng origin và credentials | JSON + guest cookie: 200; JWT sai: 401 |
| Các endpoint trên, origin lạ | Không thuộc allowlist | 403, không có Allow-Origin | GET origin lạ cũng bị chặn |

Các test CORS dùng service nghiệp vụ/JWT provider giả lập để kiểm tra HTTP, CORS, filter order và chuyển principal/cookie, không kiểm chứng lại dữ liệu cart/notification thật. Controller và JWT filter chạy thật. Bộ test chatbot hiện có vẫn chạy qua MySQL và có JWT thật.

Preflight không gửi JWT/cookie; test xác nhận CORS trả lời trước khi gọi JWT provider hoặc controller. `Authorization` và `Content-Type` được cho phép trong `Access-Control-Allow-Headers`; `Access-Control-Allow-Credentials: true` đi cùng origin cụ thể. Cookie do browser quản lý, không cần thêm `Cookie` như một header frontend tự đặt trong preflight.

## Áp dụng và kiểm tra trong browser

Backend đang chạy cần reload/restart để nạp class/property mới. Lần kiểm tra cuối sau sửa không còn listener ở :8080; test HTTP sau sửa chạy trên cổng ngẫu nhiên và tự đóng. Không dừng/kill tiến trình IDE của người dùng, không để lại server test chạy nền.

Sau khi khởi động lại backend, chạy:

```powershell
curl.exe -i -X OPTIONS http://localhost:8080/api/chatbot `
  -H "Origin: https://localhost:5173" `
  -H "Access-Control-Request-Method: POST" `
  -H "Access-Control-Request-Headers: authorization,content-type"
```

Mong đợi 200 và:

```http
Access-Control-Allow-Origin: https://localhost:5173
Access-Control-Allow-Credentials: true
Access-Control-Allow-Headers: authorization, content-type
```

Với bốn endpoint GET trong bảng, dùng `Access-Control-Request-Method: GET`. Đổi Origin sang `https://untrusted.example` phải nhận 403 và không có Allow-Origin.

Frontend dùng `credentials: 'include'` với fetch hoặc `withCredentials: true` với Axios để yêu cầu gửi/nhận cookie. Giữ Authorization Bearer cho các API cần JWT.

**Giới hạn riêng của cookie trên browser:** frontend HTTPS và backend HTTP khác scheme; SameSite xét cả scheme. Guest cookie hiện vẫn là `SameSite=Lax`, nên browser không gửi cookie đó cho cross-site fetch chỉ nhờ `credentials: include`. Đây là vấn đề chính sách cookie tách biệt với preflight CORS đã sửa. Không nới SameSite trong bản sửa này. Để giữ chính sách guest hiện tại, có thể dùng backend HTTPS hoặc proxy API qua origin HTTPS của frontend; cần kiểm tra cấu hình frontend/browser thực tế trước khi kết luận guest continuation hoạt động trên cặp URL hiện tại. Xem [MDN về SameSite](https://developer.mozilla.org/en-US/docs/Web/HTTP/Reference/Headers/Set-Cookie) và [MDN về credentials trong fetch](https://developer.mozilla.org/en-US/docs/Web/API/Fetch_API/Using_Fetch#including_credentials).
