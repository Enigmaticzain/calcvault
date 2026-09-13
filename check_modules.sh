#!/bin/bash

echo "MODULE ENABLEMENT STATUS CHECK"
echo "=============================="
echo ""

# Function to check module
check_module() {
    local name=$1
    local path=$2
    local disabled_path=$3
    local expected_count=$4
    
    if [ -d "$path" ]; then
        local count=$(find "$path" -name "*.kt" 2>/dev/null | wc -l)
        if [ $count -eq 0 ]; then
            echo "❌ $name: Directory exists but EMPTY"
        elif [ $count -eq $expected_count ]; then
            echo "✅ $name: COMPLETE ($count files)"
        else
            echo "⚠️  $name: INCOMPLETE ($count/$expected_count files)"
        fi
    else
        echo "🔴 $name: NOT FOUND"
    fi
}

check_module "Browser" "app/src/main/java/com/calcvault/browser" "disabled_sources/browser" 1
check_module "Chat" "app/src/main/java/com/calcvault/chat" "disabled_sources/chat" 4
check_module "Filters" "app/src/main/java/com/calcvault/filters" "disabled_sources/filters" 4
check_module "House" "app/src/main/java/com/calcvault/house" "disabled_sources/house" 7
check_module "Keyboard" "app/src/main/java/com/calcvault/keyboard" "disabled_sources/keyboard" 2
check_module "Mood" "app/src/main/java/com/calcvault/ui/mood" "disabled_sources/mood" 1
check_module "Notifications" "app/src/main/java/com/calcvault/notifications" "disabled_sources/notifications" 2
check_module "Settings" "app/src/main/java/com/calcvault/ui/settings" "disabled_sources/settings" 6
check_module "Sync" "app/src/main/java/com/calcvault/sync" "disabled_sources/sync" 11
check_module "WatchTogether" "app/src/main/java/com/calcvault/watchtogether" "disabled_sources/watchtogether" 2

echo ""
echo "DISABLED SOURCES INVENTORY:"
echo "============================"
ls -1 disabled_sources/ | while read dir; do
    count=$(find "disabled_sources/$dir" -name "*.kt" 2>/dev/null | wc -l)
    echo "disabled_sources/$dir: $count files"
done
