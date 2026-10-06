# Privacy

Medicine keeps your medicines and the log of what was taken on the phone, and has no way to
reach the network.

That is the whole policy. The rest of this page is the evidence for it, because a privacy
policy that cannot be checked is just a promise.

## Permissions

`app/src/main/AndroidManifest.xml` declares these, and no others:

- `SCHEDULE_EXACT_ALARM` — so a reminder rings at the minute it is due, not some minutes
  after.
- `RECEIVE_BOOT_COMPLETED` — an alarm does not survive a restart; this is how the next one is
  set again.
- `USE_FULL_SCREEN_INTENT` — so a reminder can fill the screen over the lock screen.
- `POST_NOTIFICATIONS` — asked for only on Android 13 and later; on Android 12, which the
  Kompakt runs, notifications are on unless you turn them off.

There is no `INTERNET` permission. Without it Android will not let the app open a network
connection, so nothing about your medicines could leave the phone through it even by
accident. There is no contacts permission either: choosing a pharmacy from Contacts hands the
app that one entry, through Android's own picker, and nothing else.

## What is stored

Everything is in one SQLite file in the app's own storage, `fukuyaku.db`, which no other app
can read and which is not backed up (`android:allowBackup="false"`): the medicines as you set
them up, and one row per dose with when it was due, whether it was taken or skipped, when it
was marked, and when its reminder rang. A few settings sit in `SharedPreferences`, all of them
in `data/Settings.kt`.

Uninstalling the app deletes all of it. **Settings → Clear the log** deletes the log and keeps
the medicines.

## What leaves the app, and only when you ask

- **Share as text** and **Save as CSV**, on the Log tab, hand the log to an app or a folder you
  choose.
- **Call** and **Text**, on a medicine's page, open the phone's dialler or messaging app with
  the pharmacy's number; **Put the refill in the calendar** opens the calendar's editor with
  the event filled in. Nothing is sent until you send it there.

## Other apps

Another app can ask for the list of medicines (names, amounts and when they are taken, nothing
from the log). Medicine shows you the list and the asking app's name first, and hands it over only
when you press **Share** (`share/ShareMedicinesActivity.kt`). There is no way to read it
without that press.

## The lock screen

A reminder's notification follows the phone's own lock-screen setting: where the phone hides
what notifications say, it reads "A medicine is due" and nothing more.

If the Glance app is installed and **Settings → Next dose on the lock screen** is on, Medicine
hands Glance the next dose and any dose not yet marked — medicine names and times — to show on
the lock screen, where anyone holding the phone can read them. It is off until you turn it on.
It answers Glance alone, through a provider every other app is refused by
(`glance/GlanceProvider.kt`). Nothing leaves the phone.

## No analytics

No crash reporting, no telemetry, no advertising identifier, no third-party SDK. The
dependency list in `app/build.gradle.kts` is AndroidX, Jetpack Compose and Mudita's MMD
component library, and nothing else.

## Checking for yourself

```
aapt2 dump badging app-release.apk | grep uses-permission
```

That prints every permission the built app actually carries.

Besides the four above it prints one more:

```
uses-permission: name='com.wanderwildwood.fukuyaku.DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION'
```

That one is not a permission in the sense you care about. AndroidX defines it automatically
for every app; it is a signature-level permission scoped to this package, which only this app
can hold, and it grants access to nothing.
