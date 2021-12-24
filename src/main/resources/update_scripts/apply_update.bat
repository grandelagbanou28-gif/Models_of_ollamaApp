@echo off
setlocal enabledelayedexpansion

:: GrandelGradenNexus Auto-Update Applier Script (Windows)
:: This script is executed by GrandelGradenNexus after downloading and preparing an update.
:: It lives in <GrandelGradenNexus_Root>\update\apply_update.bat

echo [GrandelGradenNexus Updater] Waiting 3 seconds for JVM to gracefully exit...
timeout /t 3 /nobreak >nul

:: Move to the parent directory (root of GrandelGradenNexus)
cd /d "%~dp0.."

echo [GrandelGradenNexus Updater] Applying update from update\new\...

if exist "update\new" (
    :: Copy files recursively and overwrite existing files without prompting (/Y)
    xcopy /S /Y "update\new\*" ".\"
    rmdir /s /q "update\new"
    echo [GrandelGradenNexus Updater] Update applied successfully.
) else (
    echo [GrandelGradenNexus Updater] ERROR: update\new\ directory not found. Aborting update.
    pause
    exit /b 1
)

echo [GrandelGradenNexus Updater] Restarting GrandelGradenNexus...

:: Launch the updated bat file in a new hidden/independent process
start "" ".\GrandelGradenNexus.bat"

echo [GrandelGradenNexus Updater] Finished.
exit /b 0
