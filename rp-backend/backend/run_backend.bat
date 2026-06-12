@echo off
echo Starting Backend...
REM Set your DB password here if it's not "rpmedia"
REM set DB_PASSWORD=your_password_here
.\gradlew.bat bootRun
pause
