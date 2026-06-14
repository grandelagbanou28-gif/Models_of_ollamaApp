#!/usr/bin/env bash

# GradenModels Auto-Update Applier Script (Mac)
# This script is executed by GradenModels after downloading and preparing an update.
# It lives in <GradenModels_Root>/update/apply_update.command

# Move to the script's directory and then one level up to the root
cd "$(dirname "$0")" || exit
cd .. || exit

echo "[GradenModels Updater] Waiting 3 seconds for JVM to gracefully exit..."
sleep 3

echo "[GradenModels Updater] Applying update from update/new/..."

# Overwrite everything recursively from update/new to the root directory
if [ -d "update/new" ]; then
    cp -Rv update/new/* ./
    rm -rf update/new
    echo "[GradenModels Updater] Update applied successfully."
else
    echo "[GradenModels Updater] ERROR: update/new/ directory not found. Aborting update."
    exit 1
fi

echo "[GradenModels Updater] Restarting GradenModels..."

# Ensure the main launcher is executable
chmod +x ./GradenModels.command

# In MacOS, the .command script is heavily preferred for UI execution
open -a Terminal.app ./GradenModels.command
echo "[GradenModels Updater] Finished."
exit 0
