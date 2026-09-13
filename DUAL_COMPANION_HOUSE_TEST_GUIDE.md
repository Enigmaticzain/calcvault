# Dual Companion House - Complete Test & Verification Guide

## Pre-Test Checklist
- [ ] All 9 tasks completed
- [ ] Code compiles without errors
- [ ] All engines initialized
- [ ] Activity registered in AndroidManifest.xml
- [ ] Callbacks wired and working

## Unit Test Scenarios

### 1. Activity Launch & Initialization
- [ ] Activity launches without crashes
- [ ] UI elements render correctly
- [ ] Initial character positions set
- [ ] Status text displays proper role assignment
- [ ] Room navigation buttons visible and clickable
- [ ] Theme chrome applied correctly

### 2. Room Navigation
- [ ] Each room button changes the scene
- [ ] Characters reposition to room start positions
- [ ] Room-specific zones are available
- [ ] Ambient animations trigger correctly

### 3. Dual Commands
- [ ] **Cook**: Both characters move to kitchen
- [ ] **Watch**: Both characters sit on sofa
- [ ] **Play**: Both characters move to activity area
- [ ] **Relax**: Both characters sit together
- [ ] Both animations trigger simultaneously
- [ ] Interaction flag set correctly

### 4. Talking Pet Controls
- [ ] Idle mode: Normal idle animation
- [ ] Listening mode: Eye tracking, nod animation
- [ ] Talking mode: Mouth movements, gestures
- [ ] Only controlled character responds to controls
- [ ] Other character dims/disables appropriately
- [ ] Scale changes correctly per mode

### 5. Care Actions
- [ ] **Feed**: Food picker dialog shows available foods
- [ ] **Bath**: Bathroom scene loads, characters animate bathing
- [ ] **Sleep**: Bedroom scene loads, sleep animation plays
- [ ] **Mini-game**: Game selector appears, room switches to garden
- [ ] Relationship meter updates
- [ ] Character mood changes based on care

### 6. Mood Controls
- [ ] Happy mood: Character jumps/dances, animation intensity increases
- [ ] Romantic mood: Characters face each other, reaching animation
- [ ] Playful mood: Animation becomes more energetic
- [ ] Tired mood: Character scale decreases, opacity dims
- [ ] Sad mood: Character looks down, slow movements
- [ ] Mood callback fires correctly

### 7. Drag & Drop Interaction
- [ ] Characters can be dragged
- [ ] Dragging generates movement commands
- [ ] Characters snap to nearest zone
- [ ] Overlap prevention works (characters don't occupy same space)
- [ ] Long-press shows context menu
- [ ] Drop results trigger appropriate actions
- [ ] Physics attraction works towards zones

### 8. Chat Message Processing
- [ ] Message triggers appropriate emotional response
- [ ] Keywords detected correctly
- [ ] Characters move to matching zones
- [ ] Dual triggers affect both characters
- [ ] Single triggers affect only one character
- [ ] Micro-animations play appropriately

### 9. Chat Trigger Coverage
Test at least 30+ unique triggers:
- [ ] Love/romance triggers (5+)
- [ ] Food/cooking triggers (3+)
- [ ] Sleep/rest triggers (3+)
- [ ] Playful triggers (3+)
- [ ] Sad/emotional triggers (3+)
- [ ] Work/focus triggers (2+)
- [ ] Special occasions (celebration, apology, etc.) (5+)
- [ ] Environmental (weather, time-based) (2+)
- [ ] Health (sick, exercise, hygiene) (3+)

### 10. Micro-Animation System
- [ ] Breathing animation loops continuously
- [ ] Blinking occurs naturally
- [ ] Nodding works on talking mode
- [ ] Waving plays on playful idle
- [ ] Head tilting on listening
- [ ] Reaching on romantic
- [ ] Tilt animation works
- [ ] Point gesture executes

### 11. Behavior Engine Updates
- [ ] Character position updates smoothly
- [ ] Movement interpolation works
- [ ] Rotation calculations correct
- [ ] Character-to-character attraction works (proximity checks)
- [ ] Emotional state affects animations
- [ ] Random delays prevent identical behavior

### 12. DragDrop Enhanced Features
- [ ] Long-press menu callback fires
- [ ] Command tap generates action
- [ ] Overlap prevention prevents z-fighting
- [ ] Attraction physics smooth
- [ ] Invalid fallback positions valid
- [ ] Command queue executes in order

### 13. Settings & Configuration
- [ ] Animation intensity slider works (0.5 - 1.5)
- [ ] Enable/disable drag-drop saves config
- [ ] Enable/disable interactions saves
- [ ] Auto-room-switch setting persists
- [ ] Debug mode toggles visualization
- [ ] Config loads on app restart
- [ ] Settings callback fires

### 14. Performance Testing
- [ ] Activity loads in < 2 seconds
- [ ] Animations run smoothly (60fps)
- [ ] No UI lag during character movement
- [ ] No memory leaks (monitor heap)
- [ ] Battery usage reasonable
- [ ] Network bandwidth minimal (local only)

### 15. Mood Integration
- [ ] UserMood converts to EmotionalState
- [ ] EmotionalState color modifier applied
- [ ] Scale modifier affects character size
- [ ] Opacity modifier dims/brightens
- [ ] Mood emoji displays correctly
- [ ] Mood intensity affects animation speed

### 16. Error Handling
- [ ] Missing layouts handled gracefully
- [ ] Null zones don't crash
- [ ] Invalid commands skip silently
- [ ] Storage errors logged
- [ ] Thread exceptions caught
- [ ] Memory cleanup on deactivate

### 17. Device Compatibility
- [ ] API 24+ (Android 7.0+)
- [ ] Portrait orientation
- [ ] Various screen sizes (phone/tablet)
- [ ] Different DPI densities
- [ ] Landscape handling (if applicable)

### 18. Lifecycle Testing
- [ ] Activity onCreate completes
- [ ] Activity onDestroy cleans up properly
- [ ] Pause/resume works correctly
- [ ] State saves/restores
- [ ] Memory freed on exit

## Performance Benchmarks

```
Activity Startup:     < 1.5s
Character Animation:  60fps
Room Change:         < 500ms
Character Movement:  Smooth @ 2.5px/frame
Message Processing:  < 200ms
Drag Operation:      Immediate
```

## Quality Checklist

- [ ] All 35+ chat triggers implemented
- [ ] 10+ micro-animations working
- [ ] Callbacks wired (mood, room, interaction, settings, message)
- [ ] Configuration system complete
- [ ] Settings persistence working
- [ ] Mood integration complete
- [ ] Physics enhancements applied
- [ ] Long-press menu implemented
- [ ] UI feedback visible
- [ ] Status text updates appropriately

## Documentation Updates

- [ ] DUAL_COMPANION_HOUSE_GUIDE.md updated
- [ ] Trigger list documented
- [ ] Settings documented
- [ ] Callback usage documented
- [ ] Code comments added
- [ ] README reflects all features

## Known Limitations & Future Work

- Lottie animations (depends on dep availability)
- Network sync (offline for now)
- Advanced AI coordination (phase 2)
- Environmental particle effects (phase 2)
- Full mini-game integration (phase 2)
- Persistent state sync to cloud (future)

## Sign-Off

- [ ] All tests passing
- [ ] No crashes observed
- [ ] Performance acceptable
- [ ] User experience smooth
- [ ] Documentation complete
- [ ] Ready for production

---

**Last Updated:** [TODAY'S DATE]
**Tested By:** [YOUR NAME]
**Status:** ✅ COMPLETE
