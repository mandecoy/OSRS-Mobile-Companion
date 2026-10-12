# OSRS Companion Tool

A free, open-source Android companion app built for Old School RuneScape Mobile.

**Current stable version: 0.5.3**  
**Android requirement: Android 8.0 (API 26) or newer**

## Features

- Custom timers with separate minute and second fields
- Non-focus floating companion overlay that stays above OSRS
- Draggable floating timer with pause, restart, stop, and repeat ON/OFF
- Toggleable weekly Tears of Guthix and daily Zaff battlestaff reminders
- Background timer notifications that can fire after the app is closed
- Timer restoration after reboot
- Farming/run timer presets
- Four independent Quick Widget slots
- Categorized, scrollable Quick Widget menus for Timers, Farm Runs, and Tools
- Custom timer Quick Widgets
- Native Puzzle Box screenshot recognition and solving
- Numbered move overlays directly on the Puzzle Box tiles
- Five-move Puzzle Box batches with Previous / Next 5 navigation
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
- Separate Quests and Achievement Diaries tools
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

Timer, reminder, Quick Widget, portfolio, watchlist, price-alert, notepad, and layout settings are stored locally on the device. Live GE tools fetch public price data from the RuneScape Wiki price API. Some tools display or link to third-party services, including the OSRS Wiki, Wise Old Man, OSRS Portal, OldSchool.tools, and Jagex websites. Those services have their own privacy policies and terms.

## Open source

The application source code is licensed under the MIT License. See [LICENSE](LICENSE).

See [CHANGELOG.md](CHANGELOG.md) for version history.

## Disclaimer

OSRS Companion Tool is an unofficial fan-made project. It is not affiliated with, endorsed by, or sponsored by Jagex Ltd.

RuneScape and Old School RuneScape are trademarks of Jagex Ltd.

## Media notice

Old School RuneScape artwork and puzzle reference images used by the app are sourced from the Old School RuneScape Wiki / game media and remain the property of their respective rights holders, including Jagex Ltd. Those media assets are not licensed under this repository's MIT software license.
