@echo off
echo Building project...
call mvn clean package

if %ERRORLEVEL% NEQ 0 (
    echo Build failed!
    pause
    exit /b 1
)

echo Starting application on http://localhost:8081
java -jar target/dependency/webapp-runner.jar --port 8081 target/ROOT.war
