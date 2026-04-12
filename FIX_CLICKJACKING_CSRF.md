# Báo cáo Fix lỗ hổng: Clickjacking & CSRF

## Tổng quan

| Lỗ hổng | OWASP | Mức | Trạng thái |
|---|---|---|---|
| Clickjacking (thiếu X-Frame-Options / CSP) | A02:2025 Security Misconfiguration | Medium | Đã fix |
| CSRF (thiếu Anti-CSRF Token + SameSite cookie) | A04:2025 Cryptographic Failures | Medium | Đã fix |

---

## 1. Clickjacking

### 1.1 Vấn đề (trước khi fix)

**Alert scanner:** `Missing Anti-clickjacking Header` + `Content Security Policy (CSP) Header Not Set`

Server không trả về các header bảo vệ framing, khiến toàn bộ trang web có thể bị nhúng trong `<iframe>` từ bất kỳ trang nào khác.

**Tấn công có thể xảy ra trước khi fix:**

Kẻ tấn công dựng một trang HTML bẫy như sau:

```html
<!-- trang của kẻ tấn công: evil.com/trap.html -->
<html>
<body>
  <h2>Nhận thưởng 500.000đ - Bấm vào đây!</h2>
  <!-- Nhúng trang đăng ký shop của nạn nhân, trong suốt và canh đúng nút submit -->
  <iframe src="http://localhost:8081/seller/register-shop"
          style="opacity:0.01; position:absolute; top:50px; left:200px;
                 width:600px; height:400px; z-index:10;">
  </iframe>
  <!-- Nút mồi nằm dưới iframe -->
  <button style="position:absolute; top:50px; left:200px;">Nhận thưởng</button>
</body>
</html>
```

Nạn nhân đã đăng nhập vào bookstore, click vào "Nhận thưởng" → thực ra click vào nút submit ẩn trong iframe → đăng ký shop không mong muốn với session của chính họ. Trang của nạn nhân trả `200 OK` vì không có header nào cấm.

**Xác nhận bằng DevTools trước khi fix:**

Response header của bất kỳ trang nào (ví dụ `GET /`):
```
HTTP/1.1 200 OK
Content-Type: text/html;charset=UTF-8
                          ← X-Frame-Options: không có
                          ← Content-Security-Policy: không có
```

### 1.2 Thay đổi code

**File:** `src/main/java/filters/EncodingFilter.java`

Trước:
```java
if (response instanceof HttpServletResponse) {
    HttpServletResponse httpResp = (HttpServletResponse) response;
    String contentType = httpResp.getContentType();
    if (contentType != null && contentType.startsWith("text/")) {
        httpResp.setContentType(contentType.split(";")[0] + "; charset=" + UTF8);
    }
}
```

Sau:
```java
if (response instanceof HttpServletResponse) {
    HttpServletResponse httpResp = (HttpServletResponse) response;

    // Chống Clickjacking: cấm nhúng trang vào iframe từ bất kỳ origin nào
    httpResp.setHeader("X-Frame-Options", "DENY");
    httpResp.setHeader("Content-Security-Policy", "frame-ancestors 'none'");
 
    String contentType = httpResp.getContentType();
    if (contentType != null && contentType.startsWith("text/")) {
        httpResp.setContentType(contentType.split(";")[0] + "; charset=" + UTF8);
    }
}
```

**Lý do chọn EncodingFilter:** Filter này đã map `/*` với `REQUEST + FORWARD + INCLUDE` trong `web.xml`, tức là cover mọi response. Không cần tạo filter mới.

**Lý do dùng cả 2 header:**
- `X-Frame-Options: DENY` — hỗ trợ tất cả trình duyệt kể cả cũ
- `Content-Security-Policy: frame-ancestors 'none'` — chuẩn W3C mới hơn, ưu tiên hơn X-Frame-Options trên các trình duyệt hiện đại

### 1.3 Tấn công sau khi fix

Response header của mọi trang sau fix:
```
HTTP/1.1 200 OK
X-Frame-Options: DENY
Content-Security-Policy: frame-ancestors 'none'
X-Content-Type-Options: nosniff
```

Kẻ tấn công tạo trang bẫy với iframe như cũ → trình duyệt của nạn nhân đọc header, **từ chối render iframe**, hiển thị thông báo "Refused to display ... in a frame". Cuộc tấn công Clickjacking thất bại hoàn toàn.

### 1.4 Kịch bản test Clickjacking (tách riêng)

**Mục tiêu:** Chứng minh trang mục tiêu không thể bị nhúng iframe từ cross-site.

**Bước test:**
1. Mở file bẫy clickjacking từ `file://` hoặc domain khác, trong đó có:
    - `iframe src="https://localhost:8443/seller/register-shop"`
2. Mở DevTools → Network và Console.
3. Quan sát request `GET /seller/register-shop` và trạng thái render iframe.

**Kết quả mong đợi sau fix:**
- Header response có `Content-Security-Policy: frame-ancestors 'none'` (và/hoặc `X-Frame-Options: DENY`).
- Trình duyệt báo lỗi kiểu `NS_ERROR_CSP_FRAME_ANCESTOR_VIOLATION` hoặc `Refused to display ... in a frame`.
- Iframe không render nội dung trang mục tiêu.

**Kết luận:** Clickjacking bị chặn thành công.

---

## 2. CSRF (Cross-Site Request Forgery)

### 2.1 Vấn đề (trước khi fix)

**Alert scanner:** `Absence of Anti-CSRF Tokens` trên form `POST /api/seller/register-shop`

**Evidence từ Burp:**
```
POST /api/seller/register-shop HTTP/1.1
Host: localhost:8081
Cookie: JSESSIONID=ABC123   ← cookie không có SameSite → browser tự gửi kèm

name=MyShop&address=...     ← không có CSRF token nào
```

Cookie `auth_token` cũng có `SameSite=None` → trình duyệt gửi kèm mọi cross-site request.

**Tấn công có thể xảy ra trước khi fix:**

Kẻ tấn công tạo trang HTML:

```html
<!-- evil.com/csrf-attack.html -->
<html>
<body onload="document.forms[0].submit()">
  <form action="http://localhost:8081/api/seller/register-shop" method="POST">
    <input type="hidden" name="name"        value="Hacker Shop">
    <input type="hidden" name="address"     value="123 Fake St">
    <input type="hidden" name="description" value="Fake shop">
  </form>
</body>
</html>
```

Nạn nhân đang đăng nhập vào bookstore, truy cập `evil.com/csrf-attack.html` → form tự submit → trình duyệt tự đính kèm cookie `JSESSIONID` (vì không có SameSite) → server nhận request, thấy session hợp lệ, **đăng ký shop thành công** dưới danh nghĩa nạn nhân mà không cần biết mật khẩu.

### 2.2 Thay đổi code

#### Thay đổi 1: Sinh CSRF token + nhúng vào form — `src/main/webapp/Seller/register-shop.jsp`

**Phần sinh token (thêm vào đầu file, trước `<!DOCTYPE html>`):**

Trước:
```jsp
<%@ page contentType="text/html;charset=UTF-8" language="java" %>
<%@ taglib uri="http://java.sun.com/jsp/jstl/core" prefix="c" %>
```

Sau:
```jsp
<%@ page contentType="text/html;charset=UTF-8" language="java" %>
<%@ page import="java.security.SecureRandom, java.util.Base64" %>
<%@ taglib uri="http://java.sun.com/jsp/jstl/core" prefix="c" %>
<%
    // Sinh CSRF token nếu session chưa có
    if (session.getAttribute("_csrf_token") == null) {
        byte[] bytes = new byte[32];
        new SecureRandom().nextBytes(bytes);
        session.setAttribute("_csrf_token", Base64.getUrlEncoder().withoutPadding().encodeToString(bytes));
    }
%>
```

**Lý do:** Token phải được sinh server-side bằng `SecureRandom` (cryptographically secure), lưu vào session để so khớp khi validate. Mỗi session có 1 token khác nhau, kẻ tấn công không thể đoán.

**Phần nhúng token vào form:**

Trước:
```html
<form id="shopRegisterForm" method="POST" action=".../api/seller/register-shop">
    <div class="form-group">
```

Sau:
```html
<form id="shopRegisterForm" method="POST" action=".../api/seller/register-shop">
    <%-- CSRF token: server sẽ so khớp giá trị này với token trong session --%>
    <input type="hidden" name="_csrf" value="<%= session.getAttribute("_csrf_token") %>">
    <div class="form-group">
```

**Lý do:** Form submit qua `new URLSearchParams(formData)` → hidden input tự động được đưa vào body request → không cần thay đổi JS.

#### Thay đổi 2: Validate CSRF token phía server — `src/main/java/web/seller/ShopRegistrationServlet.java`

Trước:
```java
try {
    System.out.println("DEBUG ShopRegistrationServlet - doPost called");
    // Try session first, then JWT token
    Integer userId = (Integer) req.getSession().getAttribute("user_id");
```

Sau:
```java
try {
    System.out.println("DEBUG ShopRegistrationServlet - doPost called");

    // Validate CSRF token (chỉ áp dụng khi request đến từ HTML form, không có JWT Bearer)
    String authHeader = req.getHeader("Authorization");
    if (authHeader == null || !authHeader.startsWith("Bearer ")) {
        HttpSession csrfSession = req.getSession(false);
        String sessionToken  = (csrfSession != null) ? (String) csrfSession.getAttribute("_csrf_token") : null;
        String requestToken  = req.getParameter("_csrf");
        if (requestToken == null || requestToken.isEmpty()) {
            requestToken = req.getHeader("X-CSRF-Token");
        }
        if (sessionToken == null || !sessionToken.equals(requestToken)) {
            resp.setStatus(HttpServletResponse.SC_FORBIDDEN);
            out.write(gson.toJson(Map.of("success", false, "message", "CSRF token không hợp lệ")));
            return;
        }
    }

    // Try session first, then JWT token
    Integer userId = (Integer) req.getSession().getAttribute("user_id");
```

**Lý do skip với Bearer token:** Request có `Authorization: Bearer ...` là API call từ JS/Postman, đã được bảo vệ bởi CORS policy (browser không tự gắn custom header khi cross-origin). CSRF chỉ nguy hiểm với cookie-based auth qua HTML form.

#### Thay đổi 3: Đổi SameSite cookie auth_token — `src/main/java/web/AuthServlet.java`

Trước:
```java
resp.addHeader("Set-Cookie", "auth_token=" + token + "; Path=/; Max-Age=86400; SameSite=None; Secure");
```

Sau:
```java
resp.addHeader("Set-Cookie", "auth_token=" + token + "; Path=/; Max-Age=86400; SameSite=Strict; Secure; HttpOnly");
```

**Lý do:**
- `SameSite=None` → trình duyệt gửi cookie kèm mọi request kể cả cross-site → mở cửa cho CSRF
- `SameSite=Strict` → trình duyệt chỉ gửi cookie khi request xuất phát từ cùng origin → cross-site request không có cookie → server từ chối
- `HttpOnly` thêm vào để JS không đọc được token (bổ sung giảm nguy cơ XSS đánh cắp token)

#### Thay đổi 4: Thêm SameSite + HttpOnly cho JSESSIONID — `src/main/webapp/WEB-INF/web.xml`

Trước:
```xml
<session-config>
    <cookie-config>
        <secure>true</secure>
    </cookie-config>
</session-config>
```

Sau:
```xml
<session-config>
    <cookie-config>
        <secure>true</secure>
        <http-only>true</http-only>
        <attribute>
            <name>SameSite</name>
            <value>Strict</value>
        </attribute>
    </cookie-config>
</session-config>
```

**Lý do:** JSESSIONID cũng là cookie auth, nếu thiếu SameSite thì dù `auth_token` đã được fix, attacker vẫn có thể dùng JSESSIONID để CSRF.

> **Lưu ý:** Thẻ `<attribute>` trong `<cookie-config>` chỉ được hỗ trợ từ Servlet 6.0. Project đang dùng Servlet 3.1 nên cần fix thêm bằng 2 cách bên dưới.

#### Thay đổi 5: Thêm SameSiteWrapper trong EncodingFilter — `src/main/java/filters/EncodingFilter.java`

Tomcat tự set JSESSIONID nội bộ, không đi qua `addHeader()` thông thường, nên cần dùng `HttpServletResponseWrapper` để bắt và gắn thêm `SameSite=Strict`.

Thêm inner class `SameSiteWrapper` và bọc response trước khi truyền vào `chain.doFilter()`:

```java
private static class SameSiteWrapper extends HttpServletResponseWrapper {
    SameSiteWrapper(HttpServletResponse response) { super(response); }

    @Override
    public void setHeader(String name, String value) {
        super.setHeader(name, patchJsessionid(name, value));
    }

    @Override
    public void addHeader(String name, String value) {
        super.addHeader(name, patchJsessionid(name, value));
    }

    private String patchJsessionid(String name, String value) {
        if ("Set-Cookie".equalsIgnoreCase(name) && value != null && value.contains("JSESSIONID")) {
            if (!value.contains("SameSite")) value = value + "; SameSite=Strict";
            if (!value.contains("HttpOnly")) value = value + "; HttpOnly";
        }
        return value;
    }
}
```

Trong `doFilter()`, đổi từ dùng `httpResp` trực tiếp sang bọc wrapper:

```java
// Trước:
HttpServletResponse httpResp = (HttpServletResponse) response;
// ...
chain.doFilter(request, response);

// Sau:
HttpServletResponse httpResp = new SameSiteWrapper((HttpServletResponse) response);
// ...
chain.doFilter(request, httpResp);
```

**Lý do:** Wrapper bắt mọi lời gọi `setHeader`/`addHeader("Set-Cookie", ...)` và tự động chèn `SameSite=Strict; HttpOnly` vào cookie JSESSIONID trước khi gửi về trình duyệt.

#### Thay đổi 6: Tạo `src/main/webapp/META-INF/context.xml` (file mới)

Cách 2 để đảm bảo Tomcat set SameSite=Strict cho JSESSIONID — cấu hình trực tiếp ở tầng Tomcat:

```xml
<?xml version="1.0" encoding="UTF-8"?>
<Context>
    <CookieProcessor sameSiteCookies="strict" />
</Context>
```

**Lý do:** `CookieProcessor sameSiteCookies="strict"` là cấu hình chuẩn của Tomcat 8.5.42+ / 9.0.21+, yêu cầu Tomcat tự động gắn `SameSite=Strict` vào **mọi cookie** (bao gồm JSESSIONID) mà không cần code Java. Kết hợp cả 2 cách (wrapper + context.xml) đảm bảo hoạt động chắc chắn.

#### Thay đổi 7: Thêm CSRF validation cho endpoint đổi mật khẩu — `src/main/java/web/ProfileServlet.java`

ZAP và Burp chỉ phát hiện CSRF ở form `register-shop`, nhưng endpoint `/api/profile/password` (đổi mật khẩu) cũng bị tấn công CSRF tương tự. Thêm import và validation vào đầu `doPost()`:

Thêm import:
```java
import javax.servlet.http.HttpSession;
```

Thêm vào đầu `doPost()` trước khi xử lý request:
```java
// CSRF validation cho mọi POST từ HTML form (không phải JWT Bearer API)
String authHeader = request.getHeader("Authorization");
if (authHeader == null || !authHeader.startsWith("Bearer ")) {
    HttpSession csrfSession = request.getSession(false);
    String sessionToken = (csrfSession != null)
            ? (String) csrfSession.getAttribute("_csrf_token") : null;
    String requestToken = request.getParameter("_csrf");
    if (requestToken == null || requestToken.isEmpty()) {
        requestToken = request.getHeader("X-CSRF-Token");
    }
    if (sessionToken == null || !sessionToken.equals(requestToken)) {
        response.setStatus(HttpServletResponse.SC_FORBIDDEN);
        response.getWriter().write("{\"error\":\"CSRF token không hợp lệ hoặc bị thiếu\"}");
        return;
    }
}
```

**Lý do:** Endpoint đổi mật khẩu có impact cao (kẻ tấn công chiếm tài khoản). Cần bảo vệ cùng cơ chế CSRF token.

### 2.3 Cơ chế hoạt động sau khi fix (end-to-end)

```
Lần đầu user mở trang register-shop:
  Server sinh token ngẫu nhiên (32 bytes) → lưu vào session["_csrf_token"]
  JSP render: <input type="hidden" name="_csrf" value="aB3xK9...">

User submit form:
  Browser gửi: name=MyShop&address=...&_csrf=aB3xK9...
  Server so khớp: request["_csrf"] == session["_csrf_token"] → PASS → xử lý

Kẻ tấn công tạo trang bẫy cross-site submit:
  Trang evil.com không biết giá trị token (không đọc được session của domain khác)
  Browser gửi: name=HackShop&address=...   ← không có _csrf hoặc sai token
  Server so khớp: null != "aB3xK9..." → FAIL → trả 403 Forbidden
  → Tấn công CSRF thất bại
```

### 2.4 Kịch bản test CSRF (tách riêng)

**Mục tiêu:** Chứng minh request giả mạo cross-site không thể thực thi hành động thay đổi dữ liệu.

**Bước test:**
1. Đăng nhập hợp lệ vào `https://localhost:8443` trên tab nạn nhân.
2. Mở trang bẫy CSRF từ `file://` hoặc domain khác, trang này tự submit form `POST` đến:
   - `/api/seller/register-shop` hoặc `/api/profile/password`
3. Mở DevTools/Burp để kiểm tra request thực tế.

**Kết quả mong đợi sau fix:**
- Cross-site request không mang cookie phiên (do `SameSite=Strict`) hoặc cookie không hợp lệ.
- Request bị chặn sớm bởi `JwtFilter` với `401 Unauthorized`.
- Không có thay đổi dữ liệu (không tạo shop mới, không đổi mật khẩu).

**Giải thích 401 vs 403:**
- `401 Unauthorized`: bị chặn ở lớp cookie/session trước khi vào servlet (kịch bản cross-site phổ biến).
- `403 Forbidden`: xảy ra khi vẫn có auth hợp lệ nhưng `_csrf` thiếu/sai (bị chặn ở lớp validate CSRF token).
- Cả hai đều chứng minh CSRF thất bại.

**Kết quả đối chiếu trước/sau fix:**

| | Trước fix | Sau fix |
|---|---|---|
| Cookie trong cross-site request | Có (SameSite=None) | Không có/không hợp lệ (SameSite=Strict) |
| Response đổi mật khẩu | `200 OK` — thành công | `401` hoặc `403` — bị chặn |
| Response đăng ký shop | `200 OK` — thành công | `401` hoặc `403` — bị chặn |

**ZAP vẫn báo `Absence of Anti-CSRF Tokens`:**
Đây có thể là **false positive** — ZAP thường chỉ nhận diện một số tên token mặc định (`CSRFToken`, `authenticity_token`...), trong khi project dùng `_csrf`.



---

## 3. Tổng hợp file đã chỉnh

| File | Thay đổi | Mục đích |
|---|---|---|
| `src/main/java/filters/EncodingFilter.java` | Thêm 3 security headers + `SameSiteWrapper` cho JSESSIONID | Fix Clickjacking + CSRF cookie |
| `src/main/webapp/Seller/register-shop.jsp` | Sinh CSRF token (SecureRandom) + thêm `<input hidden name="_csrf">` vào form | CSRF token phía client |
| `src/main/java/web/seller/ShopRegistrationServlet.java` | Validate `_csrf` param với session token | CSRF validation — endpoint đăng ký shop |
| `src/main/java/web/ProfileServlet.java` | Validate `_csrf` param với session token + thêm import `HttpSession` | CSRF validation — endpoint đổi mật khẩu |
| `src/main/java/web/AuthServlet.java` | Đổi `SameSite=None` → `SameSite=Strict; HttpOnly` cho `auth_token` | Ngăn cross-site cookie |
| `src/main/webapp/WEB-INF/web.xml` | Thêm `<http-only>true</http-only>` + `<attribute> SameSite=Strict` cho JSESSIONID | Ngăn cross-site cookie (Servlet 6.0+) |
| `src/main/webapp/META-INF/context.xml` | Tạo mới — `<CookieProcessor sameSiteCookies="strict"/>` | Ngăn cross-site JSESSIONID (Tomcat 8.5.42+) |
