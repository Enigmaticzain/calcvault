# CalcVault Theme System - Quick Reference

## Design Tokens at a Glance

### Colors
```
DARK THEME (Default)
├── Background:     @color/cv_bg_dark (#0A0A0F)
├── Surface:        @color/cv_surface_dark (#1C1C24)
├── Accent:         @color/cv_accent (#4C8EFF)
├── Text Primary:   @color/cv_text_primary_dark (#FFFFFF)
├── Text Secondary: @color/cv_text_secondary_dark (#888888)
├── Danger:         @color/cv_danger (#FF4444)
├── Success:        @color/cv_success (#44CC66)
└── Warning:        @color/cv_warning (#FFAA00)

WARM THEME
├── Background:     @color/cv_bg_warm (#1A0A10)
├── Accent:         @color/cv_accent_romantic (#FF4466)
└── Text Primary:   @color/cv_text_warm_primary (#F8F0F3)

SUBTLE THEME
├── Background:     @color/cv_bg_subtle (#F5F5F8)
├── Accent:         @color/cv_accent_subtle (#3A73E0)
└── Text Primary:   @color/cv_text_subtle_primary (#202020)
```

### Spacing (All in dp)
```
Micro:  @dimen/cv_space_xs = 4dp
Mini:   @dimen/cv_space_sm = 8dp
Small:  @dimen/cv_space_md = 12dp
Base:   @dimen/cv_space_lg = 16dp
Medium: @dimen/cv_space_xl = 20dp
Large:  @dimen/cv_space_2xl = 24dp
XLarge: @dimen/cv_space_3xl = 32dp
XXLarge: @dimen/cv_space_4xl = 40dp
```

### Border Radius (Corners)
```
Small:      @dimen/cv_radius_sm = 12dp   (inputs)
Medium:     @dimen/cv_radius_md = 18dp   (buttons)
Large:      @dimen/cv_radius_lg = 24dp   (cards)
Extra:      @dimen/cv_radius_xl = 32dp   (panels)
Circular:   @dimen/cv_radius_full = 999dp
```

### Typography Sizes (sp)
```
Display Large:   @dimen/cv_text_display_lg = 32sp
Display Medium:  @dimen/cv_text_display_md = 28sp
Headline Large:  @dimen/cv_text_headline_lg = 28sp
Title Large:     @dimen/cv_text_title_lg = 22sp
Title Medium:    @dimen/cv_text_title_md = 18sp
Body Large:      @dimen/cv_text_body_lg = 18sp
Body Medium:     @dimen/cv_text_body_md = 16sp
Body Small:      @dimen/cv_text_body_sm = 14sp
Label Medium:    @dimen/cv_text_label_md = 12sp
Caption:         @dimen/cv_text_caption = 12sp
```

### Elevation (Shadow)
```
None:     @dimen/cv_elevation_none = 0dp
Small:    @dimen/cv_elevation_sm = 2dp
Medium:   @dimen/cv_elevation_md = 4dp   (default)
Large:    @dimen/cv_elevation_lg = 8dp   (FABs)
XLarge:   @dimen/cv_elevation_xl = 12dp  (modals)
XXLarge:  @dimen/cv_elevation_2xl = 16dp (highest)
```

---

## Common UI Patterns

### Page Layout
```xml
<androidx.constraintlayout.widget.ConstraintLayout
    android:layout_width="match_parent"
    android:layout_height="match_parent"
    android:background="@drawable/bg_vault_screen"
    android:padding="@dimen/cv_space_3xl">
    <!-- Content -->
</androidx.constraintlayout.widget.ConstraintLayout>
```

### Page Title
```xml
<TextView
    style="@style/TextHeadlineLarge"
    android:text="@string/page_title"
    android:layout_marginBottom="@dimen/cv_space_2xl" />
```

### Section Title
```xml
<TextView
    style="@style/TextTitleMedium"
    android:text="@string/section_title"
    android:layout_marginTop="@dimen/cv_space_xi"
    android:layout_marginBottom="@dimen/cv_space_lg" />
```

### Primary Button
```xml
<com.google.android.material.button.MaterialButton
    style="@style/ButtonPrimary"
    android:layout_width="0dp"
    android:layout_height="@dimen/cv_button_height_lg"
    android:text="@string/action_button"
    android:layout_marginTop="@dimen/cv_space_xl" />
```

### Secondary Button
```xml
<com.google.android.material.button.MaterialButton
    style="@style/ButtonSecondary"
    android:layout_width="0dp"
    android:layout_height="@dimen/cv_button_height_md"
    android:text="@string/cancel_button" />
```

### Card/Panel
```xml
<com.google.android.material.card.MaterialCardView
    style="@style/CardVaultElevated"
    android:layout_width="0dp"
    android:layout_height="wrap_content"
    android:layout_margin="@dimen/cv_space_lg">
    <!-- Content -->
</com.google.android.material.card.MaterialCardView>
```

### Input Field
```xml
<com.google.android.material.textfield.TextInputLayout
    android:layout_width="0dp"
    android:layout_height="wrap_content">
    <com.google.android.material.textfield.TextInputEditText
        style="@style/InputFieldVault"
        android:hint="@string/input_hint" />
</com.google.android.material.textfield.TextInputLayout>
```

### Floating Action Button
```xml
<com.google.android.material.floatingactionbutton.FloatingActionButton
    style="@style/FABVault"
    android:layout_width="wrap_content"
    android:layout_height="wrap_content"
    app:fabCustomSize="@dimen/cv_fab_lg"
    android:src="@drawable/ic_action"
    android:contentDescription="@string/fab_description" />
```

### List Item with Icon
```xml
<LinearLayout
    android:layout_width="match_parent"
    android:layout_height="wrap_content"
    android:padding="@dimen/cv_space_lg"
    android:gravity="center_vertical">
    <ImageView
        android:layout_width="@dimen/cv_icon_md"
        android:layout_height="@dimen/cv_icon_md"
        android:src="@drawable/ic_item"
        app:tint="@color/cv_text_primary_dark"
        android:layout_marginEnd="@dimen/cv_space_lg" />
    <TextView
        style="@style/TextBodyMedium"
        android:text="@string/item_title" />
</LinearLayout>
```

---

## Color Usage Rules

### Always Use
```
✅ @color/cv_text_primary_dark      → Main text
✅ @color/cv_text_secondary_dark    → Supporting text
✅ @color/cv_accent                 → Actions
✅ @color/cv_success                → Positive
✅ @color/cv_danger                 → Errors
✅ @color/cv_surface_overlay        → Panels
```

### Never Use
```
❌ #FFFFFF                          → Use @color/cv_text_primary_dark
❌ #0A0A0F                          → Use @color/cv_bg_dark
❌ #4C8EFF                          → Use @color/cv_accent
❌ android:textColor="#888888"      → Use @color/cv_text_secondary_dark
```

---

## Spacing Usage Rules

### Padding/Margin Combinations
```
Micro spacing:     @dimen/cv_space_xs (4dp)
Small elements:    @dimen/cv_space_sm (8dp)
Medium elements:   @dimen/cv_space_md (12dp)
Standard padding:  @dimen/cv_space_lg (16dp)
Large sections:    @dimen/cv_space_2xl (24dp)
XL sections:       @dimen/cv_space_3xl (32dp)
```

### Standard Padding Values
```
android:padding="@dimen/cv_space_lg"           (16dp all sides)
android:paddingHorizontal="@dimen/cv_space_lg" (16dp left/right)
android:paddingVertical="@dimen/cv_space_md"   (12dp top/bottom)
android:paddingStart="@dimen/cv_space_xl"      (20dp left)
android:paddingEnd="@dimen/cv_space_xl"        (20dp right)
android:paddingTop="@dimen/cv_space_2xl"       (24dp top)
android:paddingBottom="@dimen/cv_space_2xl"    (24dp bottom)
```

---

## Text Style Mapping

### Page Titles (First heading on screen)
```
Use: @style/TextHeadlineLarge  (28sp, bold)
Example: "Vault Storage", "Call Screen"
```

### Section Titles (Subsection heading)
```
Use: @style/TextTitleMedium  (18sp, medium)
Example: "Recording Destination", "Choose Option"
```

### Body Content (Main readable text)
```
Use: @style/TextBodyMedium  (16sp, regular)
Example: Instructions, descriptions
```

### Helper Text (Secondary info)
```
Use: @style/TextBodySmall  (14sp)
Example: "0 files stored securely"
```

### Labels (Form labels, section headers)
```
Use: @style/TextLabelMedium  (12sp, bold)
Example: "RECORDING DESTINATION"
```

### Status/Error Messages
```
Use: @style/TextBodySmall with @color/cv_danger
Example: "Incorrect passphrase"
```

---

## Component Guidelines

### Button Heights (Standard)
```
Small Button:    @dimen/cv_button_height_sm = 40dp
Medium Button:   @dimen/cv_button_height_md = 48dp
Large Button:    @dimen/cv_button_height_lg = 56dp  (RECOMMENDED)
XL Button:       @dimen/cv_button_height_xl = 64dp  (For important actions)
```

### FAB (Floating Action Button) Sizes
```
Small FAB:   @dimen/cv_fab_sm = 48dp
Medium FAB:  @dimen/cv_fab_md = 64dp  (STANDARD)
Large FAB:   @dimen/cv_fab_lg = 80dp  (For primary action)
```

### Icon Sizes
```
Extra Small: @dimen/cv_icon_xs = 16dp
Small:       @dimen/cv_icon_sm = 20dp (micro icons)
Medium:      @dimen/cv_icon_md = 24dp (STANDARD)
Large:       @dimen/cv_icon_lg = 32dp (prominent icons)
XLarge:      @dimen/cv_icon_xl = 48dp (avatars)
```

### Card/Panel Styling
```
Corner Radius:  @dimen/cv_radius_xl = 32dp
Stroke Width:   @dimen/cv_stroke_thin = 1dp
Elevation:      @dimen/cv_elevation_md = 4dp
Padding:        @dimen/cv_space_xl = 20dp (content inside)
Margin:         @dimen/cv_space_lg = 16dp (around card)
```

---

## Theme Switching Reference

### Detect Emotional Content
```kotlin
if (messageContains("i love you", "good night", "good morning")) {
    setTheme(R.style.CalcVaultTheme_Warm)
}
```

### User Preference
```kotlin
when (userThemePreference) {
    "dark" -> setTheme(R.style.CalcVaultTheme)
    "warm" -> setTheme(R.style.CalcVaultTheme_Warm)
    "light" -> setTheme(R.style.CalcVaultTheme_Subtle)
}
```

---

## Accessibility Checklist

- [ ] All images have `android:contentDescription`
- [ ] Touch targets are minimum 48dp
- [ ] Text is minimum 14sp
- [ ] Color contrast ratio >= 4.5:1 for text
- [ ] No color-only information
- [ ] Buttons are labeled clearly
- [ ] Focus states are visible

---

## Common Mistakes to Avoid

### ❌ Don't do this:
```xml
<!-- Hardcoded color -->
<TextView android:textColor="#FFFFFF" />

<!-- Hardcoded size -->
<TextView android:textSize="18sp" />

<!-- Inline styling -->
<TextView
    android:textSize="16sp"
    android:textColor="#888888"
    android:fontFamily="sans-serif" />

<!-- Hardcoded padding -->
android:padding="16dp"
```

### ✅ Do this instead:
```xml
<!-- Token color -->
<TextView android:textColor="@color/cv_text_primary_dark" />

<!-- Token size -->
<TextView android:textSize="@dimen/cv_text_body_md" />

<!-- Style reference -->
<TextView style="@style/TextBodyMedium" />

<!-- Token padding -->
android:padding="@dimen/cv_space_lg"
```

---

## File Locations

```
app/src/main/res/
├── values/
│   ├── colors.xml              ← Color tokens (100+)
│   ├── dimens.xml              ← Size/spacing tokens (50+)
│   ├── styles.xml              ← Component styles (30+)
│   ├── strings.xml             ← Text labels
│   ├── themes_warm.xml         ← Warm theme variant
│   ├── themes_subtle.xml       ← Light theme variant
│   └── theme_design_system.xml ← Documentation
├── layout/
│   ├── activity_call.xml       ← Updated with tokens
│   ├── activity_vault_files.xml ← Updated with tokens
│   └── activity_passphrase.xml ← Updated with tokens
└── drawable/
    ├── bg_vault_screen.xml     ← Updated with tokens
    └── ... other drawables
```

---

## Support

For questions about the design system, refer to:
1. `THEME_SYSTEM_COMPLETE.md` - Full documentation
2. `theme_design_system.xml` - Inline comments
3. `colors.xml`, `dimens.xml`, `styles.xml` - Token definitions

**All styling decisions should reference design tokens, not hardcoded values.**

---

**Version:** 1.0  
**Last Updated:** April 15, 2026
