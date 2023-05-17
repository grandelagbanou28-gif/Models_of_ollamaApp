#!/usr/bin/env bash

# GrandelGradenNexus Rollback Script (Linux)
# This script manually restores the previous version of GrandelGradenNexus if an update fails.
# It lives in <GrandelGradenNexus_Root>/update/rollback.sh

cd "$(dirname "$0")" || exit
cd .. || exit

echo "[GrandelGradenNexus Rollback] Checking for previous version backup..."

if [ -d "update/old" ]; then
    echo "[GrandelGradenNexus Rollback] Restoring files from update/old/..."
    cp -rv update/old/* ./
    echo "[GrandelGradenNexus Rollback] Rollback completed!"
    
    echo "[GrandelGradenNexus Rollback] Restarting GrandelGradenNexus..."
    chmod +x ./GrandelGradenNexus
    nohup ./GrandelGradenNexus > /dev/null 2>&1 &
    exit 0
else
    echo "[GrandelGradenNexus Rollback] ERROR: update/old/ directory not found. Cannot perform rollback."
    exit 1
fi
