#!/usr/bin/env bash

# GradenModels Auto-Update Applier Script (Linux)
# This script is executed by GradenModels after downloading and preparing an update.
# It lives in <GradenModels_Root>/update/apply_update.sh

# Move to the script's directory and then one level up to the root
cd "$(dirname "$0")" || exit
cd .. || exit

echo "[GradenModels Updater] Waiting 3 seconds for JVM to gracefully exit..."
sleep 3

echo "[GradenModels Updater] Applying update from update/new/..."

# Overwrite everything recursively from update/new to the root directory
if [ -d "update/new" ]; then
    cp -rv update/new/* ./
    rm -rf update/new
    echo "[GradenModels Updater] Update applied successfully."
else
    echo "[GradenModels Updater] ERROR: update/new/ directory not found. Aborting update."
    exit 1
fi

echo "[GradenModels Updater] Restarting GradenModels..."

# Ensure the main launcher is executable
chmod +x ./GradenModels

# Launch the newly updated app and detach
nohup ./GradenModels > /dev/null 2>&1 &
echo "[GradenModels Updater] Finished."
exit 0
