# Signing the release build

Release APKs and the App Bundle are unsigned until you create a key. Android
refuses to install an unsigned APK, so this is the step between "it builds" and
"it runs on someone's phone".

You create the key, not the build: it is your identity as the publisher, and it
should never live in this repository or in anyone else's hands.

## 1. Create the key

Run this from the project root. Choose your own password when prompted — it is
not stored anywhere in this repo.

```sh
keytool -genkeypair -v \
  -keystore denguewatch-upload.jks \
  -storetype PKCS12 \
  -keyalg RSA -keysize 2048 \
  -validity 10000 \
  -alias upload
```

`-validity 10000` is about 27 years. Play rejects a key that expires before
2033, and a key that expires is a key you cannot ship updates with, so do not
shorten it.

It asks for a name and organisation. Any truthful value is fine; it appears in
the certificate, not in the app.

## 2. Point the build at it

```sh
cp keystore.properties.example keystore.properties
```

Then edit `keystore.properties` with the password you just chose:

```properties
storeFile=denguewatch-upload.jks
storePassword=...
keyAlias=upload
keyPassword=...
```

`keystore.properties`, `*.jks` and `*.keystore` are gitignored. If you move the
keystore outside the project, give `storeFile` an absolute path.

## 3. Build

```sh
./gradlew assembleRelease                   # per-ABI APKs, signed
./gradlew bundleRelease -PabiSplits=false   # AAB for Play, signed
```

The output filenames lose their `-unsigned` suffix once signing is active:

    app/build/outputs/apk/release/app-arm64-v8a-release.apk

Confirm before distributing anything:

```sh
$ANDROID_HOME/build-tools/<version>/apksigner verify --print-certs \
  app/build/outputs/apk/release/app-arm64-v8a-release.apk
```

Without `keystore.properties` the build still succeeds and still emits
`-unsigned` artifacts, so a fresh checkout is never blocked by a missing key.

## Backing it up

Back up `denguewatch-upload.jks` and its passwords somewhere you will still have
in five years — a password manager, not this folder.

What losing it costs depends on how you distribute:

* **Sideloaded APKs.** The key *is* the app's identity. Signing an update with a
  different key makes Android treat it as a different app: users cannot update,
  they must uninstall first, and they lose their local data with it.
* **Google Play.** New apps use Play App Signing — Google holds the real app
  signing key and you upload with this *upload* key. A lost upload key can be
  reset through Play Console support, so the situation is recoverable. It is
  still a support round-trip you would rather not need.

Either way: back it up, and never commit it.
