@echo off
cd /d "%~dp0"
call mvn -B clean package
if errorlevel 1 exit /b 1
echo.
echo Selesai: target\HeartLifesteal-1.0.0.jar
jar tf target\HeartLifesteal-1.0.0.jar
