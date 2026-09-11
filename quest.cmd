@echo off
rem Redis Java Quest CLI wrapper for Windows (cmd/PowerShell).
rem   quest list | quest seed | quest run 101-02 jedis | quest check 101-02
setlocal
cd /d %~dp0
if exist .env (
  for /f "usebackq eol=# tokens=1,* delims==" %%a in (".env") do set "%%a=%%b"
)
if not exist target\quest.jar (
  echo [quest] building target\quest.jar ...
  call mvn -q -B -DskipTests package
)
java -jar target\quest.jar %*
