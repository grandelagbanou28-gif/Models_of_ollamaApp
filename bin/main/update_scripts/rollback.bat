@echo off
setlocal enabledelayedexpansion

:: GrandelGradenNexus Rollback Script (Windows)
:: This script manually restores the previous version of GrandelGradenNexus if an update fails.
:: It lives in <GrandelGradenNexus_Root>\update\rollback.bat

cd /d "%~dp0.."

echo [GrandelGradenNexus Rollback] Checking for previous version backup...

if exist "update\old" (
    echo [GrandelGradenNexus Rollback] Restoring files from update\old\...
    xcopy /S /Y "update\old\*" ".\"
    echo [GrandelGradenNexus Rollback] Rollback completed!
    
    echo [GrandelGradenNexus Rollback] Restarting GrandelGradenNexus...
    start "" ".\GrandelGradenNexus.bat"
    exit /b 0
) else (
    echo [GrandelGradenNexus Rollback] ERROR: update\old\ directory not found. Cannot perform rollback.
    pause
    exit /b 1
)
