@echo off
setlocal
cd /d "%~dp0"
echo ==========================================
echo   Compilando Frontend...
echo ==========================================
cd frontend
call npm install
call npm run build
cd ..

echo.
echo [Copiando archivos al Backend...]
:: /S /E /Y /I son para que no pregunte nada y cree carpetas si no existen
xcopy /S /E /Y /I "frontend\dist\*" "backend\src\main\resources\public\"

echo.
echo ==========================================
echo   Compilando Backend (Java)...
echo ==========================================
cd backend
if exist mvnw.cmd (
    call mvnw.cmd clean package -DskipTests
) else (
    call mvn clean package -DskipTests
)
cd ..

echo.
echo PROCESO TERMINADO. Ya podes usar ejecutar.bat
pause