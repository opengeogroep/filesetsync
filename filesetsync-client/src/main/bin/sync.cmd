@echo off
cd %~dp0\..
:run
for /f %%i in ('dir bin\*.jar /b') do set JAR=%%i
java -jar bin\%JAR%

if not %errorlevel% == 123 goto exit

echo Updating client
rmdir /q /s bak 2>nul
xcopy /e /q /i bin bak
xcopy /e /q /y update bin
echo Running updated client
goto run

:exit
