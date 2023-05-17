#!/usr/bin/env bash

# GrandelGradenNexus Auto-Update Applier Script (Linux)
# This script is executed by GrandelGradenNexus after downloading and preparing an update.
# It lives in <GrandelGradenNexus_Root>/update/apply_update.sh

# Move to the script's directory and then one level up to the root
cd "$(dirname "$0")" || exit
cd .. || exit

echo "[GrandelGradenNexus Updater] Waiting 3 seconds for JVM to gracefully exit..."
sleep 3

echo "[GrandelGradenNexus Updater] Applying update from update/new/..."

# Overwrite everything recursively from update/new to the root directory
if [ -d "update/new" ]; then
    cp -rv update/new/* ./
    rm -rf update/new
    echo "[GrandelGradenNexus Updater] Update applied successfully."
else
    echo "[GrandelGradenNexus Updater] ERROR: update/new/ directory not found. Aborting update."
    exit 1
fi

echo "[GrandelGradenNexus Updater] Restarting GrandelGradenNexus..."

# Ensure the main launcher is executable
chmod +x ./GrandelGradenNexus

# Launch the newly updated app and detach
nohup ./GrandelGradenNexus > /dev/null 2>&1 &
echo "[GrandelGradenNexus Updater] Finished."
exit 0
