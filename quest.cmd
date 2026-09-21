@echo off
rem Redis Java Quest CLI wrapper for Windows (cmd/PowerShell).
rem   quest list | quest seed | quest run 101-02 jedis | quest check 101-02
setlocal
cd /d "%~dp0"
if exist .env (
  for /f "usebackq eol=# tokens=1,* delims==" %%a in (".env") do set "%%a=%%b"
)
powershell.exe -NoProfile -NonInteractive -Command "$ErrorActionPreference='Stop'; if ($env:QUEST_REBUILD -eq '1' -or -not (Test-Path -LiteralPath 'target/quest.jar')) { exit 1 }; $built=(Get-Item -LiteralPath 'target/quest.jar').LastWriteTimeUtc; if ((Get-Item -LiteralPath 'pom.xml').LastWriteTimeUtc -gt $built) { exit 1 }; $changed=Get-ChildItem -LiteralPath 'src' -Recurse -File | Where-Object { $_.Extension -in '.java','.json','.csv','.properties' -and $_.LastWriteTimeUtc -gt $built } | Select-Object -First 1; if ($changed) { exit 1 }; exit 0"
if not errorlevel 1 goto run
call :build
if errorlevel 1 exit /b %errorlevel%
:run
java -jar target\quest.jar %*
set code=%errorlevel%
if not "%code%"=="5" exit /b %code%
if "%~1"=="solve" goto solution
if "%~1"=="skip" goto solution
exit /b %code%

:solution
echo [quest] solucao copiada: recompilando e rodando o exercicio...
call :build
if errorlevel 1 exit /b %errorlevel%
java -jar target\quest.jar exercise "%~2" both
if errorlevel 1 exit /b %errorlevel%
java -jar target\quest.jar verify "%~2"
exit /b %errorlevel%

:build
echo [quest] building target\quest.jar ...
if exist mvnw.cmd (call mvnw.cmd -q -B -DskipTests package) else (call mvn -q -B -DskipTests package)
exit /b %errorlevel%
