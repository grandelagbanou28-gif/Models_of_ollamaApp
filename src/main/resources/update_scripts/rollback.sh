#!/usr/bin/env bash

# GradenModels Rollback Script (Linux)
# This script manually restores the previous version of GradenModels if an update fails.
# It lives in <GradenModels_Root>/update/rollback.sh

cd "$(dirname "$0")" || exit
cd .. || exit

echo "[GradenModels Rollback] Checking for previous version backup..."

if [ -d "update/old" ]; then
    echo "[GradenModels Rollback] Restoring files from update/old/..."
    cp -rv update/old/* ./
    echo "[GradenModels Rollback] Rollback completed!"
    
    echo "[GradenModels Rollback] Restarting GradenModels..."
    chmod +x ./GradenModels
    nohup ./GradenModels > /dev/null 2>&1 &
    exit 0
else
    echo "[GradenModels Rollback] ERROR: update/old/ directory not found. Cannot perform rollback."
    exit 1
fi
