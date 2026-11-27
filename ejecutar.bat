@echo off
setlocal
cd /d "%~dp0"

:: Buscamos el archivo JAR (sea cual sea su nombre exacto)
set JAR_FILE=
for /R "%~dp0backend\target" %%f in (*.jar) do (
    set "JAR_FILE=%%f"
)

if "%JAR_FILE%"=="" (
    echo.
    echo ERROR: No se encontro el archivo .jar en backend/target
    echo Por favor, ejecuta primero compilar.bat
    pause
    exit /b 1
)

echo ==========================================
echo   Iniciando Servidor en Windows...
echo ==========================================
:: Busca la línea donde dice java -jar y cambiala por esta:
java -Dfile.encoding=UTF-8 -jar "%JAR_FILE%"
pause