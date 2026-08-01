# The Brief — Android App

A native Android app that replaces your n8n morning workflow: every day at your
chosen time it fetches weather (today + tomorrow), severe weather alerts,
Bitcoin price, a configurable currency pair (EUR→NOK by default), and world
news from whichever outlets/topics you pick in Settings, then delivers a rich
morning-brief notification — fully on-device, no server, no email account
required.

Highlights added on top of the original build:
- **Configurable currency pair** — track any two currencies, not just EUR/NOK.
- **News source/topic picker** — choose which RSS outlets/topics feed your
  digest, plus an optional custom RSS feed of your own.
- **Severe weather alerts** — a red banner and notification line when a storm,
  flood, or other alert is active for your location.
- **Day summary popup** — a small "Summarize" button next to World News that
  shows a few bullet lines covering the whole day's digest.
- **Pull to refresh** — pull down anywhere on the dashboard to refresh.
- **Easier Settings** — country, currency, and other fixed-choice fields are
  now dropdown menus instead of free-text fields.

## Getting the installable APK (no coding, no Android Studio)

This project builds itself automatically in the cloud using **GitHub
Actions**, completely free. You only need a free GitHub account and a web
browser.

### Step 1 — Create a GitHub account
Go to https://github.com/signup if you don't already have one.

### Step 2 — Create a new empty repository
1. Click the **+** icon (top right) → **New repository**.
2. Name it e.g. `morning-digest`.
3. Leave everything else default, click **Create repository**.

### Step 3 — Upload this project
1. On the new repository's page, click **"uploading an existing file"**
   (or **Add file → Upload files**).
2. Open the unzipped `MorningDigest` folder on your computer, select **all**
   files and folders inside it, and drag them into the browser upload box.
   (Make sure you're uploading the *contents* of the folder, not the folder
   itself, so `app/`, `build.gradle.kts`, `.github/`, etc. sit at the root of
   the repo.)
3. Scroll down, click **Commit changes**.

### Step 4 — Let it build
1. Go to the **Actions** tab of your repository.
2. You'll see a workflow run called "Build APK" already running (it starts
   automatically on upload). Click it and wait 3–5 minutes.
3. When it finishes (green check ✅), scroll down to **Artifacts** and click
   **MorningDigest-APK** to download a zip containing `app-debug.apk`.

### Step 5 — Install on your phone
1. Transfer `app-debug.apk` to your phone (Google Drive, a USB cable,
   whatever's easiest).
2. Tap the file on your phone. Android will ask to allow installs from this
   source the first time — allow it, then tap **Install**.
3. Open the app, and allow the notification permission when asked — that's
   how the digest is delivered.

If you ever change the code and re-upload, just push new commits and repeat
Steps 4–5 — GitHub rebuilds it for you automatically every time.

## First-time setup inside the app
1. Open **Settings**.
2. Confirm your name, city/country, and notification time (defaults to
   Tyristrand, NO at 07:00) — the wake-up time you set is exactly when the
   notification arrives.
3. Make sure **Enable notifications** and **Auto-send notification daily**
   are on (they're on by default).
4. Back on the dashboard, tap **Refresh Now** to pull live data, then
   **Notify Now** to see the morning-brief notification immediately.

### Notifications, not email
There's nothing to configure beyond the schedule above — no SMTP server,
no app password, no recipient address. The digest itself (weather, Bitcoin,
EUR→NOK, and the newest headlines) is delivered as a single expandable
notification. Tapping it opens the app to the full dashboard.

If you'd rather the app just quietly refresh in the background (updating
History and the home-screen widget) without popping up a notification,
turn off **Enable notifications** in Settings — everything else keeps
working, it just won't interrupt you.

## What's inside
- Kotlin + Jetpack Compose + Material 3, MVVM, Retrofit, Coroutines,
  WorkManager, Room, repository pattern.
- Dashboard, Settings, History (last 30 reports), report detail with PDF
  export/share, home-screen widget, Quick Settings tile ("Notify Now"),
  a rich expandable morning-brief notification, offline caching, per-source
  failure isolation (if one API is down the rest of the report still comes
  through, with that section marked "Unavailable" — matching your original
  n8n workflow's behaviour).
- News is deduped across outlets, sorted newest-first, and capped at the 25
  most recent headlines; each headline is tappable and opens the full
  article in your browser.
- The APK built by the workflow is a **debug build** signed with a
  throwaway debug key — perfectly fine to install and use on your own
  phone, it just isn't suitable for uploading to the Play Store as-is.

## Latest fixes

### Currency
The currency reader now queries five sources in parallel and selects the two closest successful quotes by relative spread, averaging them for the displayed rate. Sources: Yahoo Finance, Norges Bank, ECB, Frankfurter/ECB, and ExchangeRate-API Open Access. This is more robust than trusting a single provider. See `CURRENCY_SOURCES.md`.

### Launch screen
The launch theme now uses a dark, full-bleed branded background instead of a white flash. Android 12+ still constrains the system launch icon to the OS-defined splash-icon area; Android does not permit an arbitrary full-screen launcher icon in the system splash itself.

### YouTube
Periodic polling is disabled to avoid background battery drain. Instant notifications require the owner-specific Firebase/WebSub backend setup described in `PUSH_SETUP_REQUIRED.md`.
