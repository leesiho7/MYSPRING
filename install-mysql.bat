@echo off
chcp 65001 >nul
net session >nul 2>&1
if %errorlevel% neq 0 (
    echo 관리자 권한으로 실행 중입니다...
    powershell -Command "Start-Process '%~f0' -Verb RunAs"
    exit /b
)

echo ========================================================
echo   MySQL Server 자동 설치를 시작합니다.
echo ========================================================
echo.

echo [1/2] Visual C++ Runtime 설치 중...
winget install --id Microsoft.VCRedist.2015+.x64 --exact --accept-source-agreements --accept-package-agreements

echo.
echo [2/2] MySQL Server 설치 중...
winget install --id Oracle.MySQL --exact --accept-source-agreements --accept-package-agreements

echo.
echo ========================================================
echo   설치가 완료되었습니다!
echo ========================================================
pause
