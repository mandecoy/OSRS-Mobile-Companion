# Changelog

All notable changes to OSRS Companion Tool are documented here.

## 0.5.12

### Visibility and home-screen customization
- Saved OSRS username toolbar has an eye and Hide/Show control, remembering the collapsed state across supported web tools.
- Collapsing the username controls does not delete the saved name or disable its player-field autofill.
- Reorder Widgets has an eye icon on every Quick Widget and main tool card to hide or unhide its Home-screen tile without losing the shortcut, its order, or saved settings.
- Visibility persists locally across restarts; hidden items remain in Reorder Widgets and can be restored any time.
- Hidden Quick Widget state follows the shortcut when it is dragged to another slot.
- Added Guides to the main tool reorder list.

## 0.5.11

### Guides
- Add OSRS Wiki Money Making Guide as a fourth link in the Guides hub.
- Replace OSRS Wiki's general guides directory with the Strategy Guides section.
- Change Guides tool and Quick Widget artwork to the OSRS Cake of guidance inventory icon, including previously pinned Guides shortcuts.

## 0.5.10

### Puzzle tile recognition and manual correction
- Replaced the old "tap the solved image" correction interaction that could make the desired tile difficult or impossible to select.
- Tap ANY of the 25 positions in the original screenshot and then tap another position to swap their inferred tile numbers.
- Alternatively choose the selected tile's correct SOLVED position from 1-24 or E (EMPTY) buttons; swaps maintain 25 unique tile IDs.
- Added Undo last swap and Reset tiles. Edits are retained if recalculation fails.
- Recognition with impossible inversion parity is no longer silently "fixed" by swapping arbitrary guessed tiles. Instead, the review screen blocks the solution until the player corrects the mismatched assignments.
- Added JVM regression tests for all 25 selectable tiles, blank-square corrections and tile swaps.
- RuneLite can read exact puzzle tile IDs from the client; screenshot-based mobile matching must be visually verified.

## 0.5.9

### Floating timer usability
- Rebuilt floating timer as an opaque high-contrast panel with a bold 40sp countdown and readable control rows.
- The timer overlay can adjust remaining time by ±1 minute or ±10 seconds, start a 1/5/10/15-minute preset, and toggle Repeat without opening the main app or keyboard.
- Kept Pause/Resume, Restart and Stop actions visible in comfortably sized, separate buttons; fixed the narrow/cramped Stop button.
- Added an explicit drag handle and clamps to keep floating controls inside the phone's usable screen when moved or expanded.
- Companion bubble now opens the active timer's edit controls instead of unexpectedly restarting it.
- The overlay remains FLAG_NOT_FOCUSABLE to preserve OSRS foreground focus.

## 0.5.8

### Puzzle Box reliability
- A valid solution for an incorrectly recognized board is not trustworthy. The solver now first shows the REAL uploaded puzzle beside its reconstructed board and requires explicit verification before displaying any tap instructions.
- Added picture-tile reassignment: choose a tile in the screenshot and where it belongs in the completed picture. Tile assignments swap uniquely, and users can recalculate with corrected assignments.
- Parity repairs are flagged as speculative, not quietly treated as accurate tile identification.
- The floating puzzle overlay is unavailable until puzzle recognition is verified.
- The move solver is tested with generated legal 5×5 scrambles and replayed tap sequences; these tests are part of Android build CI.
- Original screenshot needed to reproduce recognition issues with a specific live puzzle; automatic matching can still be imperfect.

### Guides
- Added a Guides hub and Quick Widget option with Theoatrix Skill Guides, Theoatrix AFK Methods, and OSRS Wiki Guides.

## 0.5.7

### Fixed
- All five Puzzle Box taps now display simultaneously as numbered markers on the reconstructed puzzle image; a move can share the same square with a later numbered tap
- Five-move groups change only with Previous 5 / Next 5; move cards are descriptive instead of advancing a single tile
- Added a drag-enabled non-focusable Puzzle Bubble with a board and group navigation, retaining OSRS foreground focus during overlay actions
- Tree and fruit-tree farming timers display mature tree artwork instead of saplings
- Timers icon uses OSRS clock/watch artwork; Floating Companion now has its own magical orb icon

### Added
- Save one RuneScape username locally across Wise Old Man, Skill Calculators, Quests, Achievement Diaries, OSRS Wiki and DPS Calculator; auto-fill and read from supported player fields where available
- Timers Quick Widget submenu contains Timers/AFK, Farming, and Dailies/Weeklies; individual reminders can be pinned and toggled
- Manage Miscellania reminder now supports 1–7 whole-day intervals (24 hours remains the default); changing the interval reschedules the alarm
- Miscellania reminders no longer mark check-ins completed automatically when a notification is delivered; "Done now" is the reset action

## 0.5.6

### Fixed

- Restored Previous 5 and Next 5 navigation instead of requiring an individual Next press for each puzzle move
- The five move cards can be tapped to preview that individual step without changing the current five-move page
- Puzzle routes now compare the old row/column solver against shorter bounded whole-board searches
- Solver paths are replay-validated and cycle-reduced before presentation
- Empty squares in reconstructed previews now show the actual screenshot's blank instead of a solved-artwork tile
- Added an interactive "Wrong EMPTY square?" correction tool that reassigns tiles and recalculates the solution
- Unusually long paths warn players to verify the screenshot's empty square

## 0.5.5

### Fixed

- Puzzle Box recognition now locks the empty slot separately so a dark artwork tile cannot be mistaken for the gap
- Puzzle previews are reconstructed from the canonical solved puzzle artwork instead of repeatedly cutting up the uploaded screenshot
- Only the current move is marked on the board; later moves are no longer drawn onto an earlier board state
- Puzzle guidance now advances one move at a time, keeping the target tile and empty space synchronized

### Changed

- Puzzle solving now uses a RuneLite-style row/column reduction strategy before the final search, replacing the long whole-board weighted search as the primary solver
- The five move cards remain visible as a group, with the current move highlighted

## 0.5.4

### Fixed

- Old School News now opens fitted to the phone width instead of loading heavily zoomed/cropped
- Pinch-to-zoom remains available for the legacy Jagex news archive
- GE item detail pages now include a button to open that exact item on the OSRS Prices Wiki
- Farm and run timer presets now use actual OSRS item/farming artwork instead of emoji
- Tree, fruit-tree, cactus, and calquat timers use their corresponding in-game sapling/seed artwork

## 0.5.3

### Changed

- Reorder Widgets now uses long-press drag and drop instead of up/down arrow buttons
- Quick Widgets can be picked up and dropped into a new slot
- Main tool tiles can be picked up and dropped into a new order
- Layout changes still save automatically on the device

## 0.5.2

### Fixed

- Puzzle Box preview now physically moves the uploaded tile images between five-move batches
- The EMPTY marker now follows the actual simulated empty tile instead of being drawn over the unchanged original screenshot
- Later batches now show the board state produced by all prior moves, making moves 6–10, 11–15, and later pages line up visually

## 0.5.1

### Changed

- GE Tools now uses a distinct Grand Exchange Clerk icon instead of sharing a generic market look
- Portfolio now uses a Platinum token icon instead of the same coins icon as GE Prices
- Notepad now uses a scroll-style notes icon instead of the generic book icon
- Existing Quick Widgets for these tools now resolve to the corrected icons automatically
- Timers use the night-cap icon and the Farm Runs selector uses the Tool Leprechaun icon
- Added a 2 hour 40 minute Coral timer preset with coral artwork
- Added a toggleable 24-hour Manage Miscellania reminder with a “Done now” reset button

## 0.5.0

### Added

- Non-focus Android overlay for the floating companion controls
- Draggable floating timer with Pause, Resume, Restart, Stop, and Repeat ON/OFF
- Native GE Tools: Flip Finder, Most Traded, Gainers, Losers, and Death's Coffer estimates
- Native High Alch profitability finder using recent traded prices, High Alch values, Nature rune cost, buy limits, and volume
- Local flipping portfolio with cost basis, realized P&L, unrealized P&L, watchlist, and periodic Android price alerts
- Individual GE item pages with recent bid/ask prices and seven-day hourly price charts
- Searchable Fairy Ring directory showing code and location
- Persistent local notepad
- Reorderable main-tool layout and Quick Widget ordering
- Toggleable weekly Tears of Guthix reminder
- Toggleable daily Zaff battlestaff reset reminder

### Changed

- Timers now include the game-reminders section
- New v0.5.0 tools are available from Quick Widgets
- Home screen can be reordered and reset to the default layout
- Personal companion data remains local; live GE information is fetched directly from the RuneScape Wiki price API

## 0.4.0

### Added

- Numbered Puzzle Box move overlays directly on the detected puzzle tiles
- Empty-tile indicator for Puzzle Box solutions
- Five-move Puzzle Box navigation with Previous and Next 5
- Native Light Box solver with clearly highlighted A-H switch guidance
- Categorized Quick Widget menus
- Scrollable Farm Runs submenu with farming timer presets
- Old School News tool

### Changed

- Quick Widget menus now use a dark OSRS-style background with readable text
- Removed redundant fixed AFK 5-minute and 10-minute Quick Widget choices
- Existing fixed AFK Quick Widgets migrate to the Timers shortcut
- OSRS Wiki, Shooting Stars, and Puzzle Box use distinct OSRS Wiki-sourced artwork
- Quests and Achievement Diaries remain separate tools and separate Quick Widget options

### Existing features

- Custom minute/second timers
- Background notifications
- Timer restoration after reboot
- Four independent Quick Widget slots
- Grand Exchange prices
- OSRS Wiki
- Wise Old Man
- Shooting Stars
- Skill calculators
- DPS calculator
- Quests
- Achievement Diaries
- Clue Helper

## 0.3.1

- Stable rescue build based on the known-working 0.2.3 app
- Restored reliable app startup
- Added safe remote OSRS Wiki artwork loading
- Added launcher resource fixes
- Added per-slot Quick Widget menus

## 0.2.3

- Added native Puzzle Box screenshot recognition
- Added separate Quests and Achievement Diaries tools
- Added custom Quick Widget timers
- Added Old School RuneScape companion-tool integrations
