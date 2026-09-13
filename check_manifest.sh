#!/bin/bash

echo "MANIFEST ACTIVITY CHECKER"
echo "========================="
echo ""

# Check which activities from disabled modules are in manifest
declare -a activities=(
    "CallActivity"
    "MoodActivity"
    "PrivateBrowserActivity"
    "SettingsActivity"
    "CoupleThemeSettingsActivity"
    "IncomingScreenShareLauncher"
    "WatchTogetherActivity"
    "FilterSettingsActivity"
    "CharacterChatActivityTemplate"
    "ScreenShareActivity"
)

MANIFEST="app/src/main/AndroidManifest.xml"

echo "Activity Status in Manifest:"
echo "----------------------------"
for activity in "${activities[@]}"; do
    if grep -q "$activity" "$MANIFEST"; then
        echo "✅ $activity - FOUND"
    else
        echo "❌ $activity - MISSING"
    fi
done

echo ""
echo "Services Status:"
echo "----------------"
services=("MultiDeviceSyncService")
for service in "${services[@]}"; do
    if grep -q "$service" "$MANIFEST"; then
        echo "✅ $service - FOUND"
    else
        echo "❌ $service - MISSING"
    fi
done
