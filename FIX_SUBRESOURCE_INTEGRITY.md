# BAO CAO FIX LO HONG: SUBRESOURCE INTEGRITY (SRI)

## 0. Executive Summary

Trang web truoc khi fix su dung nhieu tai nguyen JavaScript/CSS ben ngoai (CDN) nhung thieu thuoc tinh `integrity`.
Loi nay cho phep rui ro supply-chain: neu CDN hoac duong truyen bi can thiep, trinh duyet van chay script/CSS da bi sua.

Giai phap da ap dung:
1. Bo sung SRI cho tat ca tai nguyen external co the pin hash on dinh.
2. Bo su dung `https://cdn.tailwindcss.com` (script dong) va thay bang file local.
3. Bo cac link Google Fonts external o nhom trang admin de tranh css dong kho quan ly hash.
4. Kiem tra lai toan bo `src/main/webapp/**` de dam bao khong con external script/link thieu `integrity`.

Ket qua:
1. Loi `Sub Resource Integrity Attribute Missing` duoc xu ly o codebase webapp.
2. Build thanh cong: `mvn clean package -DskipTests` -> `BUILD SUCCESS`.

---

## 1. Thong tin lo hong

### 1.1 Ten lo hong

- Sub Resource Integrity Attribute Missing (Systemic)

### 1.2 Nhom OWASP lien quan

- OWASP Top 10: A05 Security Misconfiguration
- Co lien quan den chu de Software and Data Integrity Failures (supply-chain risk)

### 1.3 Muc do

- Muc do de xuat: Medium
- Ly do:
  1. Khong phai loi RCE truc tiep tren server.
  2. Nhung co the dan den takeover phien nguoi dung neu script CDN bi chen ma doc.
  3. Anh huong rong vi loi co tinh he thong (xuat hien tren nhieu trang).

---

## 2. Ban chat ky thuat cua lo hong

### 2.1 SRI la gi

SRI (Subresource Integrity) la co che de trinh duyet xac minh noi dung tai nguyen external truoc khi thuc thi.
Trinh duyet se:
1. Download file tai nguyen (CSS/JS).
2. Tu tinh hash (vd: SHA-384).
3. So sanh voi hash khai bao trong thuoc tinh `integrity`.
4. Neu khong khop: tu choi load tai nguyen.

Vi du:
```html
<script
  src="https://cdn.jsdelivr.net/npm/bootstrap@5.3.0/dist/js/bootstrap.bundle.min.js"
  integrity="sha384-geWF76RCwLtnZ8qwWowPQNguL3RmwHVBC9FhGdlKrxdiJJigb/j/68SIy3Te4Bkz"
  crossorigin="anonymous"></script>
```

### 2.2 Tai sao thieu SRI nguy hiem

Neu khong co SRI:
1. Trinh duyet tin tuong tuyet doi noi dung tu CDN.
2. Mot script bi thay doi (do CDN bi compromise, MITM, DNS poisoning, edge cache bi dau doc) van duoc thuc thi.
3. Script doc hai co the:
  - Doc token/session tren client.
  - Gui thong tin nguoi dung ve domain tan cong.
  - Thay doi logic form thanh toan/doi mat khau.
  - Cai keylogger tren trang dang nhap.

### 2.3 Kich ban tan cong mau

1. Ung dung include `bootstrap.bundle.min.js` tu CDN ma khong co SRI.
2. Tai mot diem edge cua CDN, file bi inject them payload trojan.
3. Nguoi dung truy cap `profile.jsp` -> trinh duyet load script da bi chen payload.
4. Payload lay token auth hoac PII va gui ra ngoai.
5. Server van tra 200 OK, khong co dau hieu tu phia backend.

---

## 3. Tai sao can fix ngay

### 3.1 Ly do bao mat

1. Cat giam rui ro supply-chain ngay tai lop browser.
2. Giam kha nang bi chiem tai khoan thong qua script ben thu ba.
3. Dat muc baseline bao mat cho static resource.

### 3.2 Ly do compliance va audit

1. Scanner (ZAP/Burp/DAST) de bao loi lap lai neu khong fix.
2. Bao cao tot nghiep/bao cao an ninh can chung minh da co hardening ro rang.
3. De bao tri: co quy trinh cap nhat hash moi khi nang cap version.

### 3.3 Trade-off duoc chap nhan

1. Moi lan doi version thu vien CDN can doi hash.
2. Giai phap local fallback cho resource dong (tailwind CDN script) tang kich thuoc repo mot chut.
3. Loi ich bao mat lon hon chi phi van hanh.

---

## 4. Pham vi bi anh huong truoc khi fix

Loi mang tinh he thong tren nhieu nhom trang:
1. Trang admin.
2. Trang seller.
3. Trang profile va mot so trang utility.
4. Cac include chung trong `WEB-INF/includes` va `WEB-INF/decorators`.

Vi vay, cach fix duoc chon la fix dong bo tren toan bo webapp thay vi tung endpoint rieng le.

---

## 5. Chien luoc fix da chon

### 5.1 Nguyen tac

1. External resource tinh (co version ro rang) -> giu CDN va them SRI.
2. External resource dong (kho pin hash an toan) -> dua ve local host.
3. Tai nguyen khong thiet yeu ben ngoai (Google Fonts css dong) -> bo de tranh rui ro/alert.

### 5.2 Mapping quyet dinh

1. `cdn.jsdelivr.net` (Bootstrap, Chart.js, Feather...) -> them `integrity`.
2. `cdnjs.cloudflare.com` (Font Awesome, Chart.js) -> them `integrity`.
3. `code.jquery.com` (jQuery) -> them `integrity`.
4. `cdn.tailwindcss.com` -> thay bang local file `assets/js/tailwindcss-cdn.js`.
5. `fonts.googleapis.com` -> bo link external tren cac trang admin co su dung.

---

## 6. Thay doi code chi tiet (before/after)

### 6.1 Vi du 1: Profile page

File: `src/main/webapp/profile.jsp`

Truoc:
```html
<link href="https://cdn.jsdelivr.net/npm/bootstrap@5.3.0/dist/css/bootstrap.min.css" rel="stylesheet">
<script src="https://cdn.jsdelivr.net/npm/bootstrap@5.3.0/dist/js/bootstrap.bundle.min.js"></script>
```

Sau:
```html
<link href="https://cdn.jsdelivr.net/npm/bootstrap@5.3.0/dist/css/bootstrap.min.css" rel="stylesheet" integrity="sha384-9ndCyUaIbzAi2FUVXJi0CjmCapSmO7SnpJef0486qhLnuZ2cdeRhO02iuK6FUUVM" crossorigin="anonymous">
<script src="https://cdn.jsdelivr.net/npm/bootstrap@5.3.0/dist/js/bootstrap.bundle.min.js" integrity="sha384-geWF76RCwLtnZ8qwWowPQNguL3RmwHVBC9FhGdlKrxdiJJigb/j/68SIy3Te4Bkz" crossorigin="anonymous"></script>
```

### 6.2 Vi du 2: Header include chung

File: `src/main/webapp/WEB-INF/includes/header.jsp`

Truoc:
```html
<script src="https://cdn.tailwindcss.com"></script>
```

Sau:
```html
<script src="<%= request.getContextPath() %>/assets/js/tailwindcss-cdn.js"></script>
```

### 6.3 Vi du 3: Admin footer

File: `src/main/webapp/WEB-INF/includes/admin/footer.jsp`

Truoc:
```html
<script src="https://code.jquery.com/jquery-3.6.0.min.js"></script>
<script src="https://cdn.jsdelivr.net/npm/bootstrap@4.6.0/dist/js/bootstrap.bundle.min.js"></script>
```

Sau:
```html
<script src="https://code.jquery.com/jquery-3.6.0.min.js" integrity="sha384-vtXRMe3mGCbOeY7l30aIg8H9p3GdeSe4IFlP6G8JMa7o7lXvnz3GFKzPxzJdPfGK" crossorigin="anonymous"></script>
<script src="https://cdn.jsdelivr.net/npm/bootstrap@4.6.0/dist/js/bootstrap.bundle.min.js" integrity="sha384-Piv4xVNRyMGpqkS2by6br4gNJ7DXjqk09RmUpJ8jgGtD7zP9yug3goQfGII0yAns" crossorigin="anonymous"></script>
```

### 6.4 Vi du 4: Admin pages bo Google Fonts external

File dai dien: `src/main/webapp/admin/AdDashboard.jsp`, `src/main/webapp/admin/AdAccount.jsp`

Truoc:
```html
<link href="https://fonts.googleapis.com/css2?family=Playfair+Display..." rel="stylesheet">
```

Sau:
```html
<!-- Da bo external google fonts; trang su dung fallback local trong CSS -->
```

---

## 7. Danh sach file da cap nhat

### 7.1 File moi

1. `src/main/webapp/assets/js/tailwindcss-cdn.js`
2. `FIX_SUBRESOURCE_INTEGRITY.md`

### 7.2 Cac file JSP/HTML da sua

1. `src/main/webapp/profile.jsp`
2. `src/main/webapp/dashboard-shipper.jsp`
3. `src/main/webapp/shipments.jsp`
4. `src/main/webapp/shipment-detail.jsp`
5. `src/main/webapp/error.jsp`
6. `src/main/webapp/admin-orders.jsp`
7. `src/main/webapp/admin-orders.html`
8. `src/main/webapp/admin-users.html`
9. `src/main/webapp/test-email.html`
10. `src/main/webapp/WEB-INF/includes/header.jsp`
11. `src/main/webapp/WEB-INF/includes/admin/header.jsp`
12. `src/main/webapp/WEB-INF/includes/admin/footer.jsp`
13. `src/main/webapp/WEB-INF/decorators/main.jsp`
14. Nhom `src/main/webapp/admin/*.jsp`
15. Nhom `src/main/webapp/Seller/*.jsp`

---

## 8. Cach tinh hash SRI da ap dung

Hash duoc tinh theo thuat toan `SHA-384` tren noi dung tai nguyen that tai thoi diem fix.

Cong thuc tinh integrity:
1. Download noi dung tai nguyen.
2. Tinh SHA-384 tren bytes noi dung.
3. Encode Base64.
4. Gan vao HTML theo mau `sha384-<base64_hash>`.

Ly do uu tien SHA-384:
1. Duoc khuyen nghi pho bien cho SRI.
2. Can bang giua do manh va kha nang tuong thich.

---

## 9. Xac minh sau fix

### 9.1 Xac minh static code

Da quet lai toan bo `src/main/webapp/**` voi regex cho external script/link va ket qua:
1. Khong con the external nao thieu `integrity`.
2. Khong con su dung `https://cdn.tailwindcss.com`.

### 9.2 Xac minh build

Command:
```bash
mvn clean package -DskipTests
```

Ket qua:
1. Build thanh cong.
2. Tao WAR thanh cong.

---

## 10. Ket luan

Loi SRI trong he thong da duoc fix theo huong phong thu nhieu lop:
1. Them SRI day du cho external static resource.
2. Dua resource dong (Tailwind CDN runtime) ve local de loai bo diem yeu.
3. Bo external Google Fonts o nhom trang admin de giam be mat tan cong.

Sau fix, ung dung dat muc hardening tot hon ro ret cho frontend supply-chain, ket hop duoc voi quy trinh scanner va bao tri release thuc te.

