@echo off
setlocal
cd /d "%~dp0"
where java >nul 2>nul || (echo Java 17+ is required. Install a JDK and try again.& pause & exit /b 1)
if not exist "target\smart-patient-management.jar" (
  where mvn >nul 2>nul || (echo Maven 3.9+ is required for the first build. Install Maven and try again.& pause & exit /b 1)
  call mvn clean package
  if errorlevel 1 (echo Build failed. Review the Maven errors above.& pause & exit /b 1)
)
java -jar "target\smart-patient-management.jar"
if errorlevel 1 pause
