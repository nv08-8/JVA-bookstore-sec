@echo off
setlocal

where mkcert >nul 2>nul
if %ERRORLEVEL% NEQ 0 (
    echo [ERROR] Khong tim thay mkcert trong PATH.
    echo Vui long cai mkcert roi mo terminal moi, sau do chay lai script.
    exit /b 1
)

echo Installing local CA (neu chua co)...
mkcert -install
if %ERRORLEVEL% NEQ 0 (
    echo [ERROR] Khong the cai local CA bang mkcert.
    exit /b 1
)

echo Generating localhost certificate files...
mkcert -cert-file localhost.pem -key-file localhost-key.pem localhost 127.0.0.1 ::1
if %ERRORLEVEL% NEQ 0 (
    echo [ERROR] Khong the tao localhost.pem / localhost-key.pem.
    exit /b 1
)

echo Generating localhost.p12 for Java/Tomcat...
mkcert -pkcs12 -p12-file localhost.p12 localhost 127.0.0.1 ::1
if %ERRORLEVEL% NEQ 0 (
    echo [ERROR] Khong the tao localhost.p12.
    exit /b 1
)

echo Done. Da tao:
echo - localhost.pem
echo - localhost-key.pem
echo - localhost.p12
echo Neu startup HTTPS bi loi password, sua KEYSTORE_PASS trong run-localhost-https.bat thanh rong.

endlocal
