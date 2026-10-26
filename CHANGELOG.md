# Changelog

All notable changes to OSRS Companion Tool are documented here.

## 0.6.0

### Floating mini dashboard / Bubble 2.0
- Replace the tall companion bubble menu with a compact two-column mini dashboard: Timer, Edit, OSRS Wiki, GE Prices, Clue Puzzle, Wiki Favorites, Swap Windows, Restart Timer, Close Web and Close Bubble.
- Open the actual OSRS Wiki and OSRS Wiki GE Prices websites as floating Android WebView windows over OSRS Mobile.
- Make each website window draggable by its header and freely resizable with a corner grip. Save separate per-window sizes and positions; fresh dual-window layouts start side by side.
- Set one or two simultaneous floating webpages in Floating Companion settings. Single-window mode replaces the existing webpage; dual mode supports two independent pages, including saved favorite pages.
- Minimize and restore each window while preserving the loaded webpage and scroll position. Optionally close immediately instead of minimizing.
- Add website Back/Forward controls, Swap Windows, and a keyboard toggle. Web browsers do not take OSRS input focus unless the user intentionally enables keyboard mode.
- Add a 55–100% window opacity slider and saved geometry controls. Keep the main bubble above the webpages so it remains clickable.
- Closing the companion bubble closes its webpage popups and retains the existing floating timer/puzzle behavior.

### Individual Wiki favorite widgets
- Add a Wiki Favorites manager: paste a valid Old School RuneScape Wiki HTTPS URL, name it and create an individual homepage widget.
- Browse the original Wiki page in Companion or open it in a resizable floating window. Pin new favorites directly from the embedded Wiki browser using the star icon.
- Support favorite widgets in the homepage reorder and visibility editor. Browse up to eight saved pages directly from the bubble's Favorites panel.
- Favorite links and layouts are saved only on the device; the app never requests a RuneScape password.

### Existing features
- Keep GE portfolio tools, puzzle and lightbox solvers, Timers, the 1–7-day Miscellania interval and other existing reminders.

## 0.5.20

### Fix embedded browser toolbar overlap with the Android status bar
- Apply Android's status-bar window inset before the 46dp custom browser header, so the App button and browser back/forward arrows are fully visible and tappable beneath the clock/status icons.
- Keep the toolbar compact. The website gets all remaining screen space and the controls remain in the same row.
- Preserve the collapsible saved-username controls and webpage history handling; no timer, reminder, or portfolio changes.

## 0.5.19

### Compact embedded browser toolbar
- Replace the full-sized application header plus separate Previous/Next row with a single compact 46dp header.
- Keep a clearly labelled App return action, website back/forward arrows (disabled when unavailable), and an optional eye icon for saved-username controls.
- Hide the separate webpage description strip and redundant username Hide/Show row to make more room for actual website content.
- Preserve previous/next browser history navigation, Android back-gesture behavior, saved username and Fill/Copy actions.
- No changes to Miscellania's restored 1–7 day schedule or existing OSRS timers.

## 0.5.18

### Restore Manage Miscellania 1–7-day schedule
- Restore the selectable 1–7-day interval slider without bringing back the Done now button.
- Keep the convenient on/off switch. The selected period persists across restarts and uses the original preference key, so previous settings from pre-0.5.17 versions are preserved.
- Changing the interval reschedules reminders immediately if enabled; activation remains the schedule anchor.
- Quick Widget labels once again identify Miscellania as a 1–7-day reminder.
- Leave the Tears of Guthix weekly on/off reminder, website Previous/Next arrows, and the streamlined Timers card unchanged.

## 0.5.17

### Simpler reminder controls
- Tears of Guthix: one on/off switch; automatically sends a weekly notification every 7 days after activation, and repeats without any Done button.
- Manage Miscellania: one on/off switch; automatically sends a notification every 24 hours after activation, and repeats without any Done button or interval slider.
- Enabling a reminder starts its schedule; disabling cancels its alarm. Restarts and reboots preserve the activation date. Upgrades with already-active old reminders safely initialize a new activation date.
- NMZ herb boxes and Zaff daily-reset reminders remain unchanged.

### Embedded web navigation
- Add persistent Previous page and Next page buttons to embedded WebViews; disable arrows when there is no history in that direction.
- Handle Android swipe-back/Back inside browser history before the app navigation stack, so web navigation no longer exits Companion prematurely.

### Timers widget
- Remove the "AFK + farming + reminders" subtitle from the Timers tool card, displaying just "Timers".
- Update Quick Widget's Miscellania description to the automatic daily schedule.

## 0.5.16

### Portfolio, watchlist and GE Tools
- Add Highest Profit sorting in GE Tools using current after-tax spread and published four-hour GE buy limits.
- Add 24-hour historical high/low trade-average analysis to watched items, with percentile-based suggested buy and sell prices, tax-adjusted potential profit, liquidity and staleness indicators.
- Add Min price, Max price (recent GE midpoint in GP) and Min 1-hour trades filters across all GE Tools modes, including a Clear filters action.
- Historical opportunities are planning estimates only: no trade completion or guaranteed margin is implied.

### Portfolio item search keyboard fix
- Resize the activity around the Android keyboard and add keyboard-safe insets to the Portfolio list.
- Automatically scroll the search field into view while typing so results don't sit behind the keyboard.
- Keep up to 15 item matches inside an independently scrollable bounded panel; selecting an item closes the keyboard.

### Puzzle recognition
- When uncertain image matching produces odd inversion parity, calculate the least-expensive solvable visual alternative with the blank position unchanged.
- Explicitly warn that this result is unverified, allow visual comparison and confirmation, and prevent falsely identifying an actual in-game puzzle as intrinsically unsolvable.
- Add tests for image-parity candidate selection and 24-hour profit calculations.

### Daily item collection notifications
- Pair Nightmare Zone Herb Boxes with Zaff's battlestaves as separately toggleable daily UTC-reset notifications.
- NMZ no longer uses short AFK timers. Remove Done buttons from both NMZ and Zaff.
- Manage Miscellania and Tears of Guthix alone retain Done and Last done tracking. Miscellania reminder settings are unchanged.
- Rename the NMZ Quick Widget to Herb Boxes.

## 0.5.15

### Floating puzzle popup and bubble lifecycle
- Size the puzzle picture from both available screen width and height, capped at 188dp instead of the original oversized 278dp.
- Add a conspicuous X close action in the top header and keep Previous 5 and Next 5 buttons on-screen on landscape phones.
- Constrain dragging to visible screen bounds.
- Closing the companion OSRS bubble dismisses its menu and puzzle popup and hides any timer panel launched through the bubble, while preserving the timer countdown. Independently launched floating timers and puzzle popups remain independent.

### Guides icon
- Bundle the actual OSRS Cake of guidance item 7542 inventory artwork locally for the Guides tool card and Quick Widget; do not rely on redirecting to a missing network file or fallback launcher icon.

### Nightmare Zone reminder
- Add an NMZ enable switch, repeating interval selectable from 5 to 30 minutes, and Done now action.
- Show elapsed time since NMZ Done now, persisted separately from the reminder scheduling baseline.
- Add NMZ as a pinnable Quick Widget under Timers / AFK and Dailies / Weeklies.

## 0.5.14

### Puzzle correction and recognition
- Replaced confusing 1–24 recognition guesses with a direct visual mapping editor: choose the cell in the original screenshot, then the corresponding artwork cell in the solved reference.
- Added accurate image-based 5×5 touch selection, blank-square assignment, and optional row/column fallback selectors.
- Remember unfinished corrections to the exact screenshot and restore them after app restarts.
- After confirming a corrected board, remember the verified appearance of each of its 24 tiles per puzzle artwork to help recognize future shuffled puzzles.
- Added a reset button for accidentally learned artwork. Previous shuffled boards are never reused as new puzzle boards.
- Compare 9 global sampling alignment offsets and combine tile colour and shape differences; use the blank image's appearance rather than matching arbitrary artwork in the solved picture.
- Screenshot parsing still requires visual confirmation and has not been proven correct on every in-game screenshot.

### Companion bubble and timers
- Companion bubble menu adds separate Show Timer / Hide Timer and Edit Timer controls.
- Hiding the timer overlay leaves the countdown running unchanged.
- Tears of Guthix and Manage Miscellania show how much time elapsed since their actual Done button was pressed, updating while the screen is open.
- Turning on Miscellania reminders no longer records a false Done button press.

### Web tool username
- Fix Fill in embedded browsers by targeting visible, focused, and labeled text fields, supporting controlled inputs and responding to dynamic site layouts.
- Show a clear status when a page has no recognizable player field, rather than failing silently. Copy remains available as a fallback.

## 0.5.13

### Compact floating timer
- Shrunk the editable timer from 292dp wide to a 192dp medium panel with compact buttons.
- Added an eye icon on both timer views to toggle a 142dp compact popout showing only the running countdown and the eye.
- The compact clock itself acts as a draggable handle.
- Kept Pause/Resume, Restart, Stop, time adjustments and preset buttons inside the medium timer; Edit controls remain collapsed until needed.
- Size selection is remembered across timer restarts, and opening the timer from the Companion bubble expands its editor.
- Mode switching does not reset the remaining time, trigger a new timer, open an Activity, or take input focus from OSRS.

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
