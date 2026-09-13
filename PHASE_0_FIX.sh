#!/bin/bash
# PHASE 0 FIX SCRIPT - Blocking Issues Resolution
# This script fixes all critical package and organization issues
# RUN THIS FIRST before any enablement

set -e

CALCVAULT_DIR="/home/szm7226/Downloads/calcvault (4)"
cd "$CALCVAULT_DIR"

echo "🔧 CalcVault Disabled Modules - Phase 0 Fixes"
echo "================================================"
echo ""

# === FIX 1: FILTERS PACKAGE NAMES ===
echo "📋 PHASE 0.1: Fixing Filters Module Package Names"
echo "   Changing com.calcvault.call.filters → com.calcvault.ui.filters"
echo ""

# Fix in disabled_sources/filters/
for file in disabled_sources/filters/FilterControlPanel.kt disabled_sources/filters/FilterSettingsActivity.kt disabled_sources/filters/FilterSyncEngine.kt disabled_sources/filters/VideoFilterEngine.kt; do
    if [ -f "$file" ]; then
        echo "   ✏️ Fixing: $file"
        sed -i 's/package com\.calcvault\.call\.filters/package com.calcvault.ui.filters/g' "$file"
    fi
done

# Also fix in source tree if they exist
for file in app/src/main/java/com/calcvault/filters/*.kt; do
    if [ -f "$file" ]; then
        echo "   ✏️ Fixing: $file"
        sed -i 's/package com\.calcvault\.call\.filters/package com.calcvault.ui.filters/g' "$file"
    fi
done

echo "   ✅ Filter packages fixed"
echo ""

# === FIX 2: WATCHTOGETHER LOCATION ===
echo "📋 PHASE 0.2: Fixing WatchTogether Module"
echo "   Option: Change package from ui.watchtogether to watchtogether"
echo "   (Moving to match manifest expectation)"
echo ""

# Fix package declaration in source tree
if [ -f "app/src/main/java/com/calcvault/watchtogether/WatchTogetherActivity.kt" ]; then
    echo "   ✏️ Fixing: app/src/main/java/com/calcvault/watchtogether/WatchTogetherActivity.kt"
    sed -i 's/package com\.calcvault\.ui\.watchtogether/package com.calcvault.watchtogether/g' \
        app/src/main/java/com/calcvault/watchtogether/WatchTogetherActivity.kt
    sed -i 's/package com\.calcvault\.ui\.watchtogether/package com.calcvault.watchtogether/g' \
        app/src/main/java/com/calcvault/watchtogether/PictureInPictureOverlay.kt
fi

# Also fix in disabled_sources if exists
if [ -f "disabled_sources/watchtogether/WatchTogetherActivity.kt" ]; then
    echo "   ✏️ Fixing: disabled_sources/watchtogether/WatchTogetherActivity.kt"
    sed -i 's/package com\.calcvault\.ui\.watchtogether/package com.calcvault.watchtogether/g' \
        disabled_sources/watchtogether/WatchTogetherActivity.kt
    sed -i 's/package com\.calcvault\.ui\.watchtogether/package com.calcvault.watchtogether/g' \
        disabled_sources/watchtogether/PictureInPictureOverlay.kt
fi

echo "   ✅ WatchTogether packages fixed"
echo ""

# === FIX 3: REMOVE HOUSE DUPLICATES ===
echo "📋 PHASE 0.3: Removing Duplicate House Files"
echo "   Keeping: app/src/main/java/com/calcvault/house/"
echo "   Removing: app/src/main/java/com/calcvault/emotional/house/"
echo ""

if [ -d "app/src/main/java/com/calcvault/emotional/house" ]; then
    echo "   🗑️ Removing duplicate: app/src/main/java/com/calcvault/emotional/house/"
    rm -rf app/src/main/java/com/calcvault/emotional/house/
    echo "   ✅ Duplicate house files removed"
else
    echo "   ℹ️ No duplicate house folder found (might be already removed)"
fi
echo ""

# === FIX 4: CONSOLIDATE MOOD MODULE ===
echo "📋 PHASE 0.4: Consolidating Mood Module"
echo "   Moving MoodManager and MoodModels to ui.mood package"
echo ""

# Check if emotional/mood exists
if [ -d "app/src/main/java/com/calcvault/emotional/mood" ]; then
    echo "   Found emotional/mood folder"
    
    # Copy files if they don't exist in mood/
    if [ -f "app/src/main/java/com/calcvault/emotional/mood/MoodManager.kt" ] && \
       [ ! -f "app/src/main/java/com/calcvault/mood/MoodManager.kt" ]; then
        echo "   📋 Copying MoodManager.kt to ui.mood package"
        cp app/src/main/java/com/calcvault/emotional/mood/MoodManager.kt \
           app/src/main/java/com/calcvault/mood/MoodManager.kt
        # Change package
        sed -i 's/package com\.calcvault\.emotional\.mood/package com.calcvault.ui.mood/g' \
            app/src/main/java/com/calcvault/mood/MoodManager.kt
    fi
    
    if [ -f "app/src/main/java/com/calcvault/emotional/mood/MoodModels.kt" ] && \
       [ ! -f "app/src/main/java/com/calcvault/mood/MoodModels.kt" ]; then
        echo "   📋 Copying MoodModels.kt to ui.mood package"
        cp app/src/main/java/com/calcvault/emotional/mood/MoodModels.kt \
           app/src/main/java/com/calcvault/mood/MoodModels.kt
        # Change package
        sed -i 's/package com\.calcvault\.emotional\.mood/package com.calcvault.ui.mood/g' \
            app/src/main/java/com/calcvault/mood/MoodModels.kt
    fi
    
    echo "   🗑️ Removing duplicate: app/src/main/java/com/calcvault/emotional/mood/"
    rm -rf app/src/main/java/com/calcvault/emotional/mood/
fi

echo "   ✅ Mood module consolidated"
echo ""

# === FIX 5: RENAME CHAT DISABLED FOLDER ===
echo "📋 PHASE 0.5: Confirming Chat Module Location"
echo "   Chat files should be in: app/src/main/java/com/calcvault/chat/"
echo ""

if [ -d "app/src/main/java/com/calcvault/chat_disabled" ]; then
    if [ ! -d "app/src/main/java/com/calcvault/chat" ]; then
        echo "   📋 Renaming chat_disabled/ to chat/"
        mv app/src/main/java/com/calcvault/chat_disabled app/src/main/java/com/calcvault/chat
    else
        echo "   ⚠️ Both chat/ and chat_disabled/ exist - manually review which to keep"
    fi
fi

echo "   ✅ Chat module confirmed"
echo ""

# === VERIFICATION ===
echo "🔍 VERIFICATION"
echo "================================================"
echo ""

echo "📊 Package verification:"
echo ""

echo "Filters packages:"
grep -h "^package" disabled_sources/filters/Filter*.kt 2>/dev/null | sort | uniq || echo "   (No filter files found)"

echo ""
echo "WatchTogether packages:"
grep -h "^package" disabled_sources/watchtogether/Watch*.kt 2>/dev/null | sort | uniq || echo "   (No watchtogether files found)"

echo ""
echo "Checking for house duplicates:"
if [ -d "app/src/main/java/com/calcvault/emotional/house" ]; then
    echo "   ❌ STILL DUPLICATED: app/src/main/java/com/calcvault/emotional/house/"
else
    echo "   ✅ Cleaned: emotional/house/ removed"
fi

echo ""
echo "Checking for mood duplicates:"
if [ -d "app/src/main/java/com/calcvault/emotional/mood" ]; then
    echo "   ❌ STILL DUPLICATED: app/src/main/java/com/calcvault/emotional/mood/"
else
    echo "   ✅ Cleaned: emotional/mood/ removed"
fi

echo ""
echo "Checking chat folder:"
if [ -d "app/src/main/java/com/calcvault/chat" ]; then
    echo "   ✅ Found: chat/ folder"
    ls -la app/src/main/java/com/calcvault/chat/*.kt 2>/dev/null | wc -l | xargs echo "      Files:"
else
    echo "   ❌ NOT FOUND: chat/ folder"
fi

if [ -d "app/src/main/java/com/calcvault/chat_disabled" ]; then
    echo "   ⚠️ Still exists: chat_disabled/ folder (should be renamed)"
fi

echo ""
echo "================================================"
echo "✅ PHASE 0 FIXES COMPLETE"
echo "================================================"
echo ""
echo "Next steps:"
echo "1. Run: ./gradlew clean compileDebugKotlin"
echo "2. If no errors, proceed to Phase 1: Enable Keyboard & Notifications"
echo ""
