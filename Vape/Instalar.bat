@echo off
title Vape V4 - Instalador (Defender)
rem Coloca a pasta do Vape nas exclusoes do Windows Defender.
rem Precisa de administrador (o script pede sozinho).

net session >nul 2>&1
if %errorlevel% neq 0 (
  echo Pedindo permissao de administrador...
  powershell -NoProfile -Command "Start-Process '%~f0' -Verb RunAs"
  exit /b
)

set "PASTA=%~dp0"
set "PASTA=%PASTA:~0,-1%"
echo.
echo Adicionando exclusao para: %PASTA%
powershell -NoProfile -ExecutionPolicy Bypass -Command "try { Add-MpPreference -ExclusionPath '%PASTA%' -ErrorAction Stop; Add-MpPreference -ExclusionProcess 'Vape421Injector.exe' -ErrorAction Stop; Write-Host ''; Write-Host 'OK: exclusao adicionada.' } catch { Write-Host ''; Write-Host 'NAO consegui via script (Tamper Protection ativado). Faca manual:'; Write-Host '  Windows Security ^> Protecao contra virus e ameacas ^> Gerenciar configuracoes ^> Exclusoes ^> Adicionar esta pasta'; }"
echo.
echo Se o .exe sumiu: Windows Security ^> Historico de protecao ^> restaure o Vape421Injector.exe.
echo Depois da exclusao, ele nao e apagado de novo.
echo.
pause
