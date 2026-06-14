@echo off
setlocal enabledelayedexpansion

:: GradenModels Auto-Update Applier Script (Windows)
:: This script is executed by GradenModels after downloading and preparing an update.
:: It lives in <GradenModels_Root>\update\apply_update.bat

echo [GradenModels Updater] Waiting 3 seconds for JVM to gracefully exit...
timeout /t 3 /nobreak >nul

:: Move to the parent directory (root of GradenModels)
cd /d "%~dp0.."

echo [GradenModels Updater] Applying update from update\new\...

if exist "update\new" (
    :: Copy files recursively and overwrite existing files without prompting (/Y)
    xcopy /S /Y "update\new\*" ".\"
    rmdir /s /q "update\new"
    echo [GradenModels Updater] Update applied successfully.
) else (
    echo [GradenModels Updater] ERROR: update\new\ directory not found. Aborting update.
    pause
    exit /b 1
)

echo [GradenModels Updater] Restarting GradenModels...

:: Launch the updated bat file in a new hidden/independent process
start "" ".\GradenModels.bat"

echo [GradenModels Updater] Finished.
exit /b 0
