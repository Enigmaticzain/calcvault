# Sanctuary Theme Quick Start Guide

## Installation Complete ✅

The "Our Sanctuary" romantic theme system has been successfully integrated into CalcVault.

## What You Get

### 7 Beautiful Couple Themes:
1. 🌙 **Twilight Stargazing** - Purple dusk, intimate starlit atmosphere
2. ☕ **Cozy Rainy Cafe** - Warm brown, coffee shop comfort
3. 🌅 **Sunrise Embrace** - Coral tones, fresh morning energy
4. 🍂 **Autumn Park Dusk** - Golden amber, romantic golden hour
5. 🌊 **Sunset Beach** - Warm orange, seaside romance
6. ❄️ **Peaceful Snow** - Cool blue, serene winter tranquility
7. 🔥 **Couch Fireplace** - Warm red, cozy hearth intimacy

## How to Access

1. Open CalcVault Settings
2. Scroll to the "Base Theme (Colors)" section
3. Look for the new "Sanctuary Themes (Romantic)" subsection
4. Tap any theme to apply instantly

## Theme Details

### Colors Used
Each theme includes carefully curated:
- **Background color** - Main vault background
- **Accent color** - Buttons, highlights, interactive elements
- **Text colors** - Primary (content) and secondary (metadata)
- **Send button color** - Theme-specific action button

### Text Contrast
All themes meet **WCAG AA accessibility** standards for text contrast.

## Technical Implementation

### Files Created/Modified
```
✅ app/src/main/res/values/colors.xml
   └─ Added 49 sanctuary theme color definitions

✅ app/src/main/res/values/themes_sanctuary.xml (NEW)
   └─ Created 21 sanctuary theme styles

✅ app/src/main/java/com/calcvault/emotional/ThemeEngine.kt
   └─ Added 7 sanctuary theme objects
   └─ Added helper functions for theme access
   └─ Updated theme resolution logic

✅ app/src/main/java/com/calcvault/ui/settings/SettingsActivity.kt
   └─ Enhanced theme picker UI
   └─ Added sanctuary theme buttons
```

### Build Status
```
✅ Kotlin compilation: SUCCESS
✅ Resource linking: SUCCESS
✅ No breaking changes
✅ Backward compatible
```

## For Developers

### Access Themes Programmatically

```kotlin
// Get a specific sanctuary theme
val theme = ThemeEngine.getThemeByType(
    ThemeEngine.ThemeType.SANCTUARY_STARGAZING
)

// Apply theme to activity
val engine = ThemeEngine(context)
engine.setTheme(ThemeEngine.ThemeType.SANCTUARY_SUNRISE)

// Get all sanctuary themes
val allThemes = ThemeEngine.getAllSanctuaryThemes()
// Returns: List<CalcVaultTheme> with 7 sanctuary themes
```

### Use in XML/Layouts

```xml
<!-- Reference sanctuary colors -->
<TextView
    android:textColor="@color/cv_sanctuary_stargazing_text_primary"
    android:background="@color/cv_sanctuary_stargazing_bg" />

<!-- Use sanctuary button styles -->
<Button style="@style/ButtonPrimary.Sanctuary.Stargazing" />
```

### Theme Data Structure

```kotlin
data class CalcVaultTheme(
    val type: ThemeType,
    val backgroundStart: Int,        // Background gradient start
    val backgroundEnd: Int,          // Background gradient end
    val surfaceColor: Int,           // Card/surface backgrounds
    val primaryText: Int,            // Main text color
    val secondaryText: Int,          // Secondary text color
    val accentColor: Int,            // Interactive elements
    val sentBubble: Int,            // Sent message bubble
    val receivedBubble: Int,        // Received message bubble
    val statusBarColor: Int,        // Status bar
    val navBarColor: Int,           // Navigation bar
    val glowColor: Int,             // Accent glow effects
    val backgroundImage: String?    // Optional bg image
)
```

## Color Palette Reference

### Twilight Stargazing
```
Background:  #0F0A1A (dark purple)
Accent:      #FF758C (pink coral)
Text Primary: #F5E8FF (light purple)
Text Secondary: #B8A0C8 (muted purple)
Send Button: #A13D3F (sunset coral)
```

### Cozy Rainy Cafe
```
Background:  #1D1B17 (warm brown)
Accent:      #E29578 (tan)
Text Primary: #F5EFE8 (cream)
Text Secondary: #C9B8A8 (muted tan)
Send Button: #845400 (amber gold)
```

### Sunrise Embrace
```
Background:  #1A1514 (deep coral)
Accent:      #FF7976 (coral)
Text Primary: #FFEEdd (warm cream)
Text Secondary: #D4B8A0 (warm tan)
Send Button: #A13D3F (sunset coral)
```

### Autumn Park Dusk
```
Background:  #1D1517 (warm brown)
Accent:      #E07A5F (terracotta)
Text Primary: #F5E8DC (light cream)
Text Secondary: #C9A8A0 (muted tan)
Send Button: #B0583B (burnt orange)
```

### Sunset Beach
```
Background:  #1D0E12 (deep maroon)
Accent:      #FFAC81 (warm orange)
Text Primary: #FFEAE0 (light peach)
Text Secondary: #D9B8A8 (warm tan)
Send Button: #B85A3C (sunset clay)
```

### Peaceful Snow
```
Background:  #101623 (navy arctic)
Accent:      #A2C2E8 (soft blue)
Text Primary: #E8F0FF (light blue)
Text Secondary: #A8C0D8 (muted blue)
Send Button: #2B4C7E (steel cobalt)
```

### Couch Fireplace
```
Background:  #201517 (deep sienna)
Accent:      #F39C12 (warm gold)
Text Primary: #FFF0E0 (light cream)
Text Secondary: #D4B8A0 (warm tan)
Send Button: #A34A2E (hearth terracotta)
```

## Integration Source

**Original Project**: Our Sanctuary (React/TypeScript)
- Theme designs created in Material Stitch
- Interactive components: Cosmic Fields, Rain Effects, Sunrise Pulse, etc.
- Couple-focused messaging interface
- Romantic visual backdrops

**Colors Extracted From**:
- Stitch Design System: https://stitch.withgoogle.com/projects/1965716305730493559
- Our Sanctuary Project Theme Config

## Features

### ✨ What Works
- Theme selection in Settings
- Instant theme switching
- Theme persistence across app restarts
- Proper color theming across all UI elements
- Text contrast accessibility
- Integration with SessionManager
- Backward compatible with existing themes

### 🚀 Future Enhancements
- Animated particle effects for each theme
- User-customizable theme variants
- Time-based automatic theme switching
- Theme sharing between users
- Context-aware theme selection

## Support & Troubleshooting

### Theme Not Applying?
- Ensure you're on the latest build
- Restart the app after selection
- Check that Adaptive Theme is disabled for testing

### Colors Look Wrong?
- Verify device display calibration
- Check that dark mode system setting matches expected behavior
- WCAG AA contrast ratios are maintained

### Need Help?
- Check `SANCTUARY_THEME_INTEGRATION.md` for detailed documentation
- Review `ThemeEngine.kt` source code
- Check `SettingsActivity.kt` for integration patterns

## Success Metrics

✅ All 7 sanctuary themes integrated
✅ Colors match original design system
✅ Settings UI updated and functional
✅ Build succeeds without errors
✅ Themes persist across sessions
✅ No breaking changes to existing code

---

**Status**: Ready for Production ✅
**Last Updated**: June 2, 2026
**Version**: 1.0
