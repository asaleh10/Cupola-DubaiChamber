@echo off
REM Compiles the DCCI IVR API classes into .\out for CLI testing.
REM Requires a JDK (javac) on the PATH. Works with JDK 8 and newer.
setlocal
cd /d "%~dp0"
if not exist out mkdir out
javac -encoding UTF-8 -cp lib\json-20240303.jar -d out *.java
if errorlevel 1 (
    echo BUILD FAILED
    exit /b 1
)
echo BUILD OK - classes are in .\out
echo.
echo Examples:
echo   java -cp "out;lib\json-20240303.jar" flow.ValidateMembershipClient 1298
echo   java -cp "out;lib\json-20240303.jar" flow.UserProfileClient 97121234234
echo   java -cp "out;lib\json-20240303.jar" flow.ServiceRequestStatusClient sr 120802411138
echo   java -cp "out;lib\json-20240303.jar" flow.PaymentLinkClient 120804978308
endlocal
