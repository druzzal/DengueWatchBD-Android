# Play Console — Data Safety answers

Every answer below is taken from the code, not assumed. The evidence is named
so you can re-check it if a reviewer questions anything.

## The headline answers

| Question | Answer | Why |
|---|---|---|
| Does your app collect or share any of the required user data types? | **No** | Play defines collection as transmitting data **off the device**. The only outbound requests are two parameterless `GET`s for public files (`FeedService.kt`) — no body, no user data, no identifiers. Location never reaches the networking layer. |
| Is all user data encrypted in transit? | **Yes** | Every endpoint is HTTPS. There is no `http://` anywhere in the app, and cleartext is blocked by platform default at this targetSdk. |
| Do you provide a way to request data deletion? | Data never leaves the device; uninstalling removes it. Answer the deletion question accordingly. |

## Why "no collection" is the correct answer

This is the one judgement call, so here is the reasoning in full.

The app **does** read your location, and it **does** store a symptom log. Both
stay on the device:

- Location is used by `LocationManager.kt`, `Geofencing.kt` and
  `AreaEntryWatch.kt`, all on-device. No latitude or longitude value is ever
  passed to `FeedService.kt`, the only class that opens a network connection.
- The symptom log is a file in the app's private storage
  (`CaseLogStore.kt` → `filesDir/case-log.json`).
- Both are excluded from Android cloud backup and device-to-device transfer by
  `res/xml/data_extraction_rules.xml`, with `allowBackup="false"`. That matters
  here: without it, Android would have copied the symptom log to the user's
  Google Drive, which **would** have been off-device transfer of health data
  and would have had to be declared.

Play's guidance is explicit that data processed only on the device is not
"collected". So the answer is No.

## What is not in the app

Confirmed absent by dependency inspection — declare none of these:

- No analytics SDK
- No advertising SDK or ad identifier
- No crash reporting SDK
- No Firebase of any kind
- No accounts, login or authentication
- No device or user identifiers collected

The full third-party dependency list is: AndroidX/Compose, Google Play Services
Location (on-device APIs only), MapLibre (map rendering), and WorkManager.

## Sensitive types a reviewer may ask about

- **Location.** Accessed, not collected. Precise and background location
  permissions are requested; see the separate background-location declaration.
- **Health information.** The symptom log is health data. It is stored locally
  and never transmitted, so it is not collected under Play's definition.
