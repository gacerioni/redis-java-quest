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
  if exist mvnw.cmd (call mvnw.cmd -q -B -DskipTests package) else (call mvn -q -B -DskipTests package)
)
java -jar target\quest.jar %*
set code=%errorlevel%
if "%code%"=="5" (
  echo [quest] solucao copiada: recompilando e rodando o exercicio...
  if exist mvnw.cmd (call mvnw.cmd -q -B -DskipTests package) else (call mvn -q -B -DskipTests package)
  java -jar target\quest.jar exercise %2 both
  java -jar target\quest.jar verify %2
  exit /b %errorlevel%
)
exit /b %code%
