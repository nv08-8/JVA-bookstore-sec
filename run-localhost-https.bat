@echo off
setlocal

pushd "%~dp0"

set "KEYSTORE_PATH=localhost.p12"
set "KEYSTORE_PASS=changeit"
set "HTTPS_PORT=8443"

if not exist "%KEYSTORE_PATH%" (
    echo [ERROR] Khong tim thay file keystore: "%KEYSTORE_PATH%"
    echo Dat file localhost.p12 cung thu muc voi script nay, hoac sua KEYSTORE_PATH trong file.
    exit /b 1
)

echo Building project...
set "MAVEN_OPTS=-Xms256m -Xmx512m"
call mvn clean package

if %ERRORLEVEL% NEQ 0 (
    echo Clean build failed. Retrying without clean...
    call mvn package
    if %ERRORLEVEL% NEQ 0 (
        echo Build failed!
        exit /b 1
    )
)

echo Starting application on https://localhost:%HTTPS_PORT%
if "%KEYSTORE_PASS%"=="" (
    set "JAVA_OPTS=-Djavax.net.ssl.keyStore=%KEYSTORE_PATH% -Djavax.net.ssl.keyStoreType=PKCS12"
) else (
    set "JAVA_OPTS=-Djavax.net.ssl.keyStore=%KEYSTORE_PATH% -Djavax.net.ssl.keyStorePassword=%KEYSTORE_PASS% -Djavax.net.ssl.keyStoreType=PKCS12"
)
java %JAVA_OPTS% -jar target/dependency/webapp-runner.jar --port %HTTPS_PORT% --enable-ssl target/ROOT.war

popd
endlocal