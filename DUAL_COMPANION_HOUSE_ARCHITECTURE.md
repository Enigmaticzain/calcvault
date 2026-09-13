# Dual Companion House - Architecture Overview

```
┌─────────────────────────────────────────────────────────────────────────┐
│                   DUAL COMPANION HOUSE THEME ARCHITECTURE               │
└─────────────────────────────────────────────────────────────────────────┘

                              USER INTERACTIONS
                                    │
                    ┌───────────────┼───────────────┐
                    │               │               │
                 TOUCH          COMMANDS           MOODS
              (Drag/Drop)      (Buttons)       (Emotions)
                    │               │               │
                    └───────────────┼───────────────┘
                                    │
                        ┌───────────▼───────────┐
                        │  DragDropInteraction  │
                        │     Engine            │
                        │ - Zone Detection      │
                        │ - Command Generation  │
                        └───────────┬───────────┘
                                    │
                                    ▼
                    ┌─────────────────────────────┐
                    │  Chat Messages (Network)    │
                    │  "miss you", "cook", etc    │
                    └──────────────┬──────────────┘
                                   │
                            ┌──────▼──────┐
                            │   ChatTrigger
                            │    Engine    │
                            │ - Keyword    │
                            │   Matching   │
                            │ - Action Map │
                            └──────┬───────┘
                                   │
                                   ▼
                    ┌──────────────────────────────┐
                    │    CommandExecutor           │
                    │ - Queue Management           │
                    │ - Execution Timeline         │
                    └──────────────┬───────────────┘
                                   │
                                   ▼
                    ┌──────────────────────────────┐
                    │   CharacterBehaviorEngine    │
                    │ - Position Updates           │
                    │ - Micro-Animation Values     │
                    │ - Emotion State Changes      │
                    └──────────────┬───────────────┘
                                   │
                                   ▼
                    ┌──────────────────────────────┐
                    │  HouseEnvironmentView        │
                    │ - Canvas Rendering           │
                    │ - Character Drawing          │
                    │ - Animation Frames           │
                    │ - Touch Event Handling       │
                    └──────────────┬───────────────┘
                                   │
                                   ▼
                            SCREEN DISPLAY
```

---

## 🔄 DATA FLOW

### 1. DRAG & DROP FLOW
```
User Touch Event
    ↓
HouseEnvironmentView.onTouchEvent()
    ↓
DragDropInteractionEngine.handleTouchEvent()
    ↓
Calculate Drop Position
    ↓
Detect Nearest Zone
    ↓
Generate CharacterCommand
    ↓
Queue Command
    ↓
CommandExecutor.executeCommand()
    ↓
CharacterBehaviorEngine.updateCharacter()
    ↓
Character Position/Action Updated
    ↓
HouseEnvironmentView.setCharacterState()
    ↓
Canvas Re-draw Next Frame
    ↓
User Sees Animation
```

### 2. CHAT TRIGGER FLOW
```
Message Arrives
    ↓
DualCompanionHouseApplicator.onMessageReceived()
    ↓
ChatTriggerEngine.processMessage()
    ↓
Match Keywords Against Rules
    ↓
Generate Commands:
  - Movement Command
  - Action Command
  - Mood Command
    ↓
CommandExecutor.executeCommand() (for each)
    ↓
CharacterBehaviorEngine.applyCommand()
    ↓
Character State Updates
    ↓
View Re-renders
    ↓
User Sees Reaction
```

### 3. ANIMATION LOOP
```
HouseEnvironmentView.scheduleNextFrame()
    ↓ (every 16ms @ 60 FPS)
onDraw(canvas)
    ↓
updateCharacterAnimations()
    ↓
CharacterBehaviorEngine.updateDualCharacters()
    ↓
Update Positions, Micro-animations, Rotations
    ↓
drawCharacter() for Zain
    ↓
drawCharacter() for Sanu
    ↓
drawInteractionConnection() if together
    ↓
Canvas Updates & Display
    ↓
View.invalidate() → next frame
```

---

## 📦 COMPONENT DETAILS

### HouseModels.kt
**Provides:** Data structures and enums

```
Enums:
├── HouseRoom (LIVING, KITCHEN, BEDROOM, ACTIVITY)
├── ZoneType (SOFA, TV, KITCHEN, BALCONY, BED, GAME_AREA, DOOR, FLOOR)
├── CharacterIdentity (ZAIN, SANU)
├── EmotionalState (HAPPY, SAD, TIRED, ROMANTIC, PLAYFUL, NEUTRAL, FOCUSED, RESTING)
├── CharacterAction (IDLE, WALKING, SITTING, COOKING, WATCHING_TV, SLEEPING, ...)
├── ActionType (GO_TO_ZONE, DO_ACTION, INTERACT_TOGETHER, PLAY_ANIMATION, ...)
└── MicroAnimationType (BLINK, BREATHING, EYE_TRACK, HEAD_TURN, SHIFT_WEIGHT, ...)

Data Classes:
├── InteractionZone - Physical area for character interaction
├── RoomLayout - Room definition with zones
├── CharacterState - Individual character state
├── DualCharacterState - Both characters + interaction state
├── CharacterCommand - Executable action
├── CommandSequence - Multiple commands
└── MicroAnimation - Small animation definition
```

### HouseEnvironment.kt
**Provides:** Physical room and zone layout

```
Key Methods:
├── initializeRooms() - Creates 4 rooms with zones
├── getRoomLayout(room) - Gets room definition
├── getZonesForRoom(room) - Gets all zones in room
├── getZoneAtPosition(pos, room) - Finds zone at touch position
├── getNearestZone(pos, maxDist) - Finds closest zone
├── getCharacterStartPositions(room) - Initial character placement
└── isValidPosition(pos, room) - Bounds checking

Zone Details per Room:
LIVING ROOM:
├── Sofa Zone (for sitting together)
└── Floor Zone (for standing/idle)

KITCHEN:
├── Counter Zone (for cooking together)
└── Floor Zone

BEDROOM:
├── Bed Zone (for sleeping together)
└── Floor Zone

ACTIVITY:
└── Game Zone (for playing together)
```

### CharacterBehaviorEngine.kt
**Provides:** Character movement and animation logic

```
Key Methods:
├── updateCharacter() - Update single character per frame
├── updateDualCharacters() - Update both + handle interactions
├── moveTowardTarget() - Smooth path finding
├── updateAnimationProgress() - Progress cycles
├── getMicroAnimations() - Get animations for current state
├── getAnimationValue() - Calculate animation frame value
└── applyCommand() - Execute command on character

Animation Types:
├── Breathing (sine wave, 3000ms cycle)
├── Blinking (fast pulse, 100ms)
├── Swaying (gentle oscillation)
├── Fidgeting (random jitter)
├── Weight shifts (alternating stance)
└── Head nods (up-down motion)

Movement:
├── Walk speed: 2.5 px/frame
├── Turn speed: 15°/frame
├── Distance-based arrival detection
└── Smooth angle interpolation
```

### DragDropInteractionEngine.kt
**Provides:** Touch input handling and zone detection

```
Touch Event Flow:
ACTION_DOWN → Detect touched character
ACTION_MOVE → Record drag position
ACTION_UP → Calculate drop, generate command

Detection:
├── Character hit radius: 60px
├── Drag threshold: 10px (to distinguish drag vs tap)
├── Long press duration: 500ms
└── Zone attraction radius: 80px

Command Generation:
├── Movement command with target position
├── Action command if zone has action
├── Optional partner notification
└── Queued for execution

Query Methods:
├── getDragState() - Current drag info
├── isDragging() - Boolean check
├── getNextCommand() - Pop from queue
└── getPendingCommands() - Peek all
```

### CommandExecutor.kt
**Provides:** Command queueing and execution management

```
Execution:
├── Queue commands
├── Track execution start times
├── Detect completion (2s default duration)
├── Clean up expired commands

Methods:
├── executeCommand(cmd, state) → updated state
├── updateExecutingCommands() → cleanup
├── queueCommand() → add to queue
├── getExecutingCommands() → view active
└── cancelCommand(id) → abort execution
```

### HouseEnvironmentView.kt
**Provides:** Canvas-based rendering and main game loop

```
Rendering Pipeline:
1. Draw room background (color from RoomLayout)
2. Update character animations (physics, micro-anims)
3. Draw Zain character
4. Draw Sanu character
5. Draw interaction connection line (if together)
6. Draw drag preview (while dragging)
7. Schedule next frame

Character Drawing:
├── Body (circle)
├── Head (circle with emoji indicator)
├── Arms (with action-specific poses)
├── Eyes (pupils track movement)
├── Emotion indicator (emoji above head)
└── Action visualization (cooking pot, game ball, etc.)

Animation Updates (every frame):
├── Breathing sine wave
├── Micro-animation values
├── Position interpolation
├── Rotation smoothing
└── Zone detection
```

### ChatTriggerEngine.kt
**Provides:** Message keyword to action mapping

```
Default Rules (30+):
├── Love/Romance: "love", "miss you" → HOLDING_HANDS, ROMANTIC
├── Cooking: "cook", "food" → COOKING, KITCHEN zone
├── Sleep: "good night" → SLEEPING, BEDROOM
├── Play: "game", "fun" → PLAYING, ACTIVITY zone
├── TV: "watch", "movie" → WATCHING_TV, SOFA zone
├── Emotional: "sad" → LISTENING, SAD mood
└── ... and more

Processing:
1. Extract keywords from message
2. Match against rules
3. Generate commands for character(s)
4. Apply emotional states
5. Coordinate both characters (if rule.affectsPartner)
6. Queue animations

Customization:
├── addCustomTrigger() - Add new rule
├── removeTrigger() - Remove by keyword
└── getTriggers() - List all active
```

### DualCompanionHouseApplicator.kt
**Provides:** Integration point and state management

```
Lifecycle:
├── activate(activity, container) - Initialize & attach views
└── deactivate() - Cleanup & persist

State Management:
├── Persist config to StorageManager (JSON)
├── Load config from storage
├── Save character positions
├── Load character positions on startup
└── Auto-save on config change

Integration Methods:
├── onMessageReceived(from, content) - Hook point for chat
├── executeCommand(command) - Execute any command
├── changeRoom(room) - Switch environments
├── setCharacterMood(id, mood) - Change emotion
└── getCharacterState() - Query current state

Config Options:
├── enableCharacters (show/hide)
├── enableDragDrop (allow touch)
├── enableInteractions (character coordination)
├── animationIntensity (0.5-1.5 scale)
├── autoRoomSwitch (rotate rooms)
├── currentRoom (initial room)
└── debugMode (show zones)
```

### DualCompanionHouseActivity.kt
**Provides:** User interface and controls

```
Buttons:
ROOM NAVIGATION:
├── 🛋️ Living Room
├── 🍳 Kitchen
├── 🛏️ Bedroom
└── 🎮 Activity Area

QUICK COMMANDS:
├── 👨‍🍳 Cook Together
├── 📺 Watch TV
├── 🎮 Play Game
└── 🧘 Relax

MOOD BUTTONS:
├── 😊 Happy
├── 😍 Romantic
├── 😄 Playful
├── 😴 Tired
└── 😔 Sad

Displays:
├── Room background (dynamic)
├── Both characters (animated)
├── Current state info text
└── Touch feedback (drag preview)
```

---

## 🎯 STATE FLOW EXAMPLE

### "Cook Together" Sequence

```
1. User taps "Cook" button
   ↓
2. DualCompanionHouseActivity.cmdCook.setOnClickListener()
   ↓
3. executeCommandSequence() with 2 commands:
   - ZAIN: GO_TO_ZONE(KITCHEN)
   - SANU: GO_TO_ZONE(KITCHEN)
   ↓
4. DualCompanionHouseApplicator.executeCommand() x2
   ↓
5. CommandExecutor.executeCommand()
   ├─ For ZAIN:
   │  - CharacterBehaviorEngine.applyCommand()
   │  - ZAIN.targetPosition = kitchen counter
   │  - ZAIN.isWalking = true
   │  - ZAIN.action = WALKING
   │
   └─ For SANU:
      - Same as above
   ↓
6. Next frame cycle:
   HouseEnvironmentView.onDraw()
   ├─ CharacterBehaviorEngine.updateDualCharacters()
   ├─ moveTowardTarget() for both
   ├─ drawCharacter() positions legs for walking
   ├─ Check if reached target
   ├─ When close enough:
   │  - isWalking = false
   │  - action = COOKING
   │  - animationProgress = 0
   │
   └─ drawActionVisualization()
      - Draw stirring pot animation
   ↓
7. Continuous rendering until next command
```

---

## 🔧 EXTENSION POINTS

### Add a New Room
1. Create zone definitions in `HouseEnvironment.initializeRooms()`
2. Add background color
3. Suggest character positions
4. Reference in chat triggers

### Add a New Chat Trigger
1. Create `ChatTriggerResponse` with keywords
2. Add to `triggerRules` in `ChatTriggerEngine`
3. Test with keyword in chat

### Custom Character Appearance
1. Override `HouseEnvironmentView.drawCharacter()`
2. Change colors, shapes, animations
3. Add custom emoji or expressions

### New Micro-Animation Type
1. Add to `MicroAnimationType` enum
2. Implement in `CharacterBehaviorEngine.getAnimationValue()`
3. Return appropriate float value
4. Integrate with character drawing

---

**Version:** 1.0  
**Architecture Pattern:** MVC (Model-View-Controller)  
**Threading:** Single-threaded (main thread, Canvas-based)  
**Persistence:** JSON via StorageManager
