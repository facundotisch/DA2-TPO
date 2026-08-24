@echo off
echo ===================================================
echo   Compilando y Desplegando StayHub ServicioDeHoteles
echo ===================================================

set "JDK_BIN=C:\Program Files\Eclipse Adoptium\jdk-17.0.10.7-hotspot\bin"
set "WILDFLY_DIR=%~dp0..\wildfly-41.0.0.Final"
set "PROJECT_DIR=%~dp0"

if not exist "%WILDFLY_DIR%" (
    set "WILDFLY_DIR=%~dp0wildfly-41.0.0.Final"
)

powershell -NoProfile -ExecutionPolicy Bypass -Command "& '%PROJECT_DIR%build-and-deploy.ps1'"

echo ===================================================
echo   Proceso finalizado.
echo ===================================================
pause
