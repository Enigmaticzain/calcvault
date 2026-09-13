#!/bin/bash
# CalcVault Pipeline Verification Script
# Verifies all pipeline components are in place

set -e

echo "╔════════════════════════════════════════════════════════╗"
echo "║  CalcVault Pipeline Implementation Verification       ║"
echo "╚════════════════════════════════════════════════════════╝"
echo ""

GREEN='\033[0;32m'
RED='\033[0;31m'
YELLOW='\033[1;33m'
NC='\033[0m' # No Color

passed=0
failed=0

test_file() {
    local file=$1
    local desc=$2
    
    if [ -f "$file" ]; then
        echo -e "${GREEN}✅${NC} $desc"
        ((passed++))
    else
        echo -e "${RED}❌${NC} $desc - File not found: $file"
        ((failed++))
    fi
}

test_dir() {
    local dir=$1
    local desc=$2
    
    if [ -d "$dir" ]; then
        echo -e "${GREEN}✅${NC} $desc"
        ((passed++))
    else
        echo -e "${RED}❌${NC} $desc - Directory not found: $dir"
        ((failed++))
    fi
}

echo "PHASE 1: Build Configuration"
echo "───────────────────────────"
test_file "app/build.gradle" "Build configuration with testing"
test_file ".ktlint.yml" "KtLint style configuration"
echo ""

echo "PHASE 2: Core Components"
echo "───────────────────────────"
test_file "app/src/main/java/com/calcvault/core/init/AppInitializer.kt" "App initializer"
test_file "app/src/main/java/com/calcvault/core/health/HealthCheckManager.kt" "Health check manager"
echo ""

echo "PHASE 3: Unit Tests"
echo "───────────────────────────"
test_file "app/src/test/java/com/calcvault/crypto/E2EKeyManagerTest.kt" "Crypto tests"
test_file "app/src/test/java/com/calcvault/sync/MultiDeviceSyncEngineTest.kt" "Sync tests"
test_file "app/src/test/java/com/calcvault/chat/ChatMessageTest.kt" "Chat tests"
test_file "app/src/test/java/com/calcvault/test/utils/TestUtils.kt" "Test utilities"
echo ""

echo "PHASE 4: CI/CD Pipeline"
echo "───────────────────────────"
test_file ".github/workflows/build-and-test.yml" "GitHub Actions workflow"
echo ""

echo "Documentation Files"
echo "───────────────────────────"
test_file "INTEGRATION_PIPELINE_GUIDE.md" "Integration guide"
test_file "PIPELINE_IMPLEMENTATION_STATUS.md" "Implementation status"
test_file "PIPELINE_QUICK_START.md" "Quick start guide"
echo ""

echo "════════════════════════════════════════════════════════"
echo "Results: ${GREEN}${passed} passed${NC}, ${RED}${failed} failed${NC}"
echo "════════════════════════════════════════════════════════"
echo ""

if [ $failed -eq 0 ]; then
    echo -e "${GREEN}✅ All pipeline components verified successfully!${NC}"
    echo ""
    echo "Next steps:"
    echo "1. Run tests: ./gradlew testAll"
    echo "2. Format code: ./gradlew ktlintFormat"
    echo "3. Build APK: ./gradlew assembleDebug"
    echo "4. Review integration guide: cat INTEGRATION_PIPELINE_GUIDE.md"
    exit 0
else
    echo -e "${RED}❌ Some components are missing. Review the setup.${NC}"
    exit 1
fi
