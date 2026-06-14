@echo off
setlocal enabledelayedexpansion

:: GradenModels Rollback Script (Windows)
:: This script manually restores the previous version of GradenModels if an update fails.
:: It lives in <GradenModels_Root>\update\rollback.bat

cd /d "%~dp0.."

echo [GradenModels Rollback] Checking for previous version backup...

if exist "update\old" (
    echo [GradenModels Rollback] Restoring files from update\old\...
    xcopy /S /Y "update\old\*" ".\"
    echo [GradenModels Rollback] Rollback completed!
    
    echo [GradenModels Rollback] Restarting GradenModels...
    start "" ".\GradenModels.bat"
    exit /b 0
) else (
    echo [GradenModels Rollback] ERROR: update\old\ directory not found. Cannot perform rollback.
    pause
    exit /b 1
)
