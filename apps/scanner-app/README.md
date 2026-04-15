# scanner-app

Expo app used by gate operators to scan consumer ticket QR codes. Talks to
`POST /v1/op/tickets/redeem` on the gateway with a Firebase ID token.

## Local development

```bash
cd apps/scanner-app
bun install
cp .env.example .env.local
# fill in EXPO_PUBLIC_API_BASE_URL + EXPO_PUBLIC_FIREBASE_*
bun expo start
```

Scan the Expo Go QR with a phone on the same network, or press `i`/`a` to
launch the iOS simulator / Android emulator.

## Internal distribution via EAS

Real operator phones don't have the Expo dev server. Use EAS Build to produce
sideloadable artifacts.

### One-time setup

```bash
npm install -g eas-cli
eas login                         # uses a free Expo account
eas project:init                  # links this app to Expo's servers
```

### Development build (recommended for ongoing iteration)

Produces a `.apk` + `.ipa` with the dev client bundled. Once installed,
subsequent JS changes ship over-the-air via `eas update`, no rebuild needed.

```bash
eas build --profile development --platform ios      # or android
```

EAS shows a QR code at the end; operator scans it on-device to install.

### Preview build (release-candidate testing)

No dev client; closer to what a store build would look like. Still signed for
internal distribution — does not require App Store / Play Store approval.

```bash
eas build --profile preview --platform ios
```

### Pushing JS updates after a build lands

```bash
eas update --channel development --message "..."
eas update --channel preview --message "..."
```

The app's `updates.channel` must match; it's read from `eas.json`'s profile
channel at build time.

## What's NOT set up

- TestFlight / Play Store submission. Requires paid Apple Developer ($99/yr)
  and Google Play ($25 one-time) accounts. Deferred until there's a real
  production rollout.
- Automated EAS builds in Cloud Build. Operator builds are run manually
  today; CI integration is future work.
