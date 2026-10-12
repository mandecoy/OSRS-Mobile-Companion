# OSRS Companion Tool

A free, open-source Android companion app built for Old School RuneScape Mobile.

**Current stable version: 0.5.12**  
**Android requirement: Android 8.0 (API 26) or newer**

## Features

- Custom timers with separate minute and second fields
- Non-focus floating companion overlay that stays above OSRS
- Opaque, higher-contrast floating timer with a large countdown, readable buttons, drag handle and screen-edge clamping
- Adjust floating time without taking input focus away from OSRS: add/subtract 1 minute or 10 seconds, choose 1/5/10/15-minute presets, and toggle repeat
- Draggable floating timer with pause, restart, stop, and repeat ON/OFF
- Toggleable weekly Tears of Guthix and daily Zaff battlestaff reminders
- Manage Miscellania reminder with customizable 1-7-day interval and Done now reset
- Background timer notifications that can fire after the app is closed
- Timer restoration after reboot
- Farming timer icons show grown trees rather than saplings
- Farming/run timer presets
- Four independent Quick Widget slots, with Timers → AFK / Farming / Dailies & Weeklies categories
- Reorder screen eye icons hide or restore individual Quick Widgets and main tool cards without losing their order or settings
- Categorized, scrollable Quick Widget menus for Timers, Farm Runs, and Tools
- Custom timer Quick Widgets
- Native Puzzle Box screenshot recognition and solving
- Required screenshot-versus-recognized-board review before any solution is shown
- Correct ANY recognized tile by tapping two squares in the actual screenshot to swap labels, or selecting a solved target 1–24 / EMPTY; undo, reset, and recalculate
- Impossible image recognitions are blocked rather than silently modified to fit the solver
- Flag speculative parity corrections so the user knows recognition might be wrong
- Numbered move overlays directly on the Puzzle Box tiles
- All five Puzzle Box taps numbered on the board at once, with Previous 5 / Next 5 navigation
- Non-focusable floating Puzzle Box over OSRS with its own group navigation
- Manual EMPTY-square correction when screenshot matching picks the wrong blank
- Native Light Box solver with highlighted A-H switch guidance
- Native GE Tools with flip finder, most-traded items, gainers/losers, Death's Coffer estimates, and item price charts
- Native High Alch profitability finder using recent prices, Nature rune cost, volume, and buy limits
- Local flipping portfolio with realized/unrealized P&L, watchlist, and periodic price alerts
- Searchable Fairy Ring directory with code and location
- Persistent local notepad
- Reorderable main tools and Quick Widgets
- Grand Exchange prices
- OSRS Wiki
- Wise Old Man
- Shooting Stars tracker
- Skill calculators
- RuneScape Wiki DPS calculator
- One saved OSRS username shared across Wise Old Man, Skill Calculators, Quest/Diary reference pages, Wiki and DPS Calculator; profile fields autofill on supported services
- One-tap eye icon collapses username controls across web tools without deleting the saved username or stopping autofill
- Separate Quests and Achievement Diaries tools
- Guides hub with Theoatrix Skill Guides, Theoatrix AFK Methods, OSRS Wiki Strategy Guides, and OSRS Wiki Money Making Guide; Cake of guidance icon
- Clue Helper
- Official Old School News archive
- OSRS Wiki-sourced artwork throughout the app
- Gnome-inspired launcher icon

## Installation

For normal users, publish a signed APK through the repository's **Releases** section.

Once a release is published:

1. Open the latest release.
2. Download `OSRS-Companion-Tool-x.x.x.apk`.
3. Open the APK on your Android device.
4. If Android asks, allow your browser or file manager to install unknown apps.
5. Install **OSRS Companion Tool**.

The APKs produced by the regular **Android APK** workflow are debug builds intended for testing. For public distribution, use the signed-release workflow documented in [docs/RELEASING.md](docs/RELEASING.md).

## Building from source

Requirements:

- JDK 17
- Gradle 8.9
- Android SDK / compileSdk 35

Build a debug APK:

```bash
gradle :app:assembleDebug
```

The output is:

```text
app/build/outputs/apk/debug/app-debug.apk
```

## Privacy

OSRS Companion Tool has no account system and no analytics.

Timer, reminder, Quick Widget, portfolio, watchlist, price-alert, notepad, saved player name, and layout settings are stored locally on the device. Live GE tools fetch public price data from the RuneScape Wiki price API. Some tools display or link to third-party services, including the OSRS Wiki, Wise Old Man, OSRS Portal, OldSchool.tools, and Jagex websites. Those services have their own privacy policies and terms.

## Open source

The application source code is licensed under the MIT License. See [LICENSE](LICENSE).

See [CHANGELOG.md](CHANGELOG.md) for version history.

## Disclaimer

OSRS Companion Tool is an unofficial fan-made project. It is not affiliated with, endorsed by, or sponsored by Jagex Ltd.

RuneScape and Old School RuneScape are trademarks of Jagex Ltd.

## Media notice

Old School RuneScape artwork and puzzle reference images used by the app are sourced from the Old School RuneScape Wiki / game media and remain the property of their respective rights holders, including Jagex Ltd. Those media assets are not licensed under this repository's MIT software license.
