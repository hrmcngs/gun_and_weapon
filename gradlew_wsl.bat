@echo off
cd /d "%~dp0"
set "JAVA_HOME=C:\Program Files\Java\jdk-17"
call gradlew.bat %*
