# KacperOps 🍼💧💩
*Eat · Transform · Load*: a tiny Android app for logging a newborn's feeds, pees and poops on two phones.

## Install
Download `KacperOps.apk` from the latest [Release](../../releases/latest) on each phone, open it and allow installing. It updates the earlier "Baby Log" build in place (same package and signing key).

## Sync setup (once, ~3 min)
Both phones read and write `log.json` on the **`data` branch of this repo** through the GitHub API. The repo is private, so the log is too.

1. **Create a token.** GitHub → Settings → Developer settings → Personal access tokens → **Fine-grained tokens** → *Generate new token*.
   - Repository access: **Only select repositories → `kacper-ops`**
   - Permissions → Repository permissions → **Contents: Read and write** (nothing else)
   - Expiration: the longest it offers. When it expires, paste a new one.
2. **Phone 1:** ⚙ → *Sync between phones* → repository `melanore/kacper-ops`, branch `data`, paste the token → **Save & sync**.
3. **Phone 2:** on the welcome screen tap **Join with partner's QR** (or ⚙ → *Scan QR*) and scan the QR from phone 1 (⚙ → *QR*).

Sync runs on app open, every 30 s while open (conditional requests, so rate limits don't matter), and right after every tap. If both phones save at the same instant, one retries with a merge. The newest edit wins per entry, and deletes sync too. The `data` branch history is a full audit log.

## Seeded history
`log.json` on `main` holds the entries from 21–25 Oct copied from the family chat. When the first phone sets up sync,
the app creates the `data` branch from `main`, so it starts with this history and merges in whatever is already on the phone.

## Using it
- Time defaults to **now** (24 h, rounded to 10 min), with −10/−5/+5/+10 min buttons. Tap an hour and a `:00–:50` to log something earlier. It snaps back to *Now* after 3 idle minutes.
- Step 1: tap **Pee / Poop / Pee + Poop / Feed**. *Feed* first asks **how much** (30/40/50/60 ml in one tap, or −5/+5 up to 160 ml).
- Step 2: confirm the time and tap **Log at …**. Back always goes one step back, and you can switch the type on the time step.
- Tap any history entry to edit its type, day, time, note or who logged it, or to delete it.
- Tiles show the time since the last feed / pee / poop. The feed tile turns red after 3 h.
- ⚙: people, theme (System/Light/Dark, Material You colours), language (EN/PL/UK), CSV export.

## Build
GitHub Actions builds every push to `main` (`.github/workflows/build.yml`), publishes a Release with the APK, and renders UI screenshots (Robolectric + Roborazzi) to the `ci-output` branch.
Stack: Kotlin, Jetpack Compose, Material 3, zxing (QR). Local build: `gradle :app:assembleRelease` (Gradle 8.11, JDK 17).
