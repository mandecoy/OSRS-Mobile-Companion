# Changelog

All notable changes to OSRS Companion Tool are documented here.

## 0.5.4

### Fixed

- Old School News now opens fitted to the phone width instead of loading heavily zoomed/cropped
- Pinch-to-zoom remains available for the legacy Jagex news archive

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
