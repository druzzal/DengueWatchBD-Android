# Upload key — facts and recovery notes

**This file contains no secrets.** Everything here is either public (it is
embedded in every APK you ship) or a pointer. The password is deliberately not
recorded anywhere in this repository.

## The key

| | |
|---|---|
| File | `denguewatch-upload.jks` (project root, gitignored) |
| Alias | `upload` |
| Type | PKCS12, RSA 2048 |
| Certificate | `CN=uzzal tangchangya, OU=medical, O=UCT, L=Dhaka, ST=Bangladesh, C=BD` |
| SHA-256 | `97:B3:89:19:07:0A:CF:77:62:EB:23:96:CE:62:D7:45:A8:4B:EA:E8:A4:59:66:DC:35:27:20:3D:2A:2E:8F:28` |
| SHA-1 | `E7:DA:5E:90:A8:C2:7E:D1:13:90:8C:46:7E:67:FF:B5:DA:3D:44:EB` |
| First signed | `app-release.aab`, 9 September 2026 |

Those fingerprints are how you prove later that a given build came from this
key. Play Console shows the same values once you upload, under
**Setup → App integrity**. If they ever disagree, something is signing with a
different key and updates will be rejected.

## What must be backed up

Two things, and neither is in version control:

1. `denguewatch-upload.jks`
2. The password (store and key, currently the same value)

Back them up **together but not in the same place as the source code**. A
password manager entry with the `.jks` attached is the usual answer. A copy on
the same disk as this repository protects against nothing that matters — the
realistic failure is losing the machine, not deleting one file.

## What happens if it is lost

Depends entirely on how the app is distributed:

- **Google Play with Play App Signing** (the default for new apps): Google holds
  the real app signing key; this is only the *upload* key. A lost upload key can
  be reset through Play Console support. Recoverable, but a support round-trip.
- **Sideloaded APKs**, outside Play: this key *is* the app's identity. Signing an
  update with a different key makes Android treat it as a different app —
  existing users cannot update, they must uninstall first, and they lose their
  local data (chosen area, settings, symptom log) with it.

## What must never happen

- Do not commit either file. `.gitignore` covers `*.jks`, `*.keystore` and
  `keystore.properties`, and `git log --all` confirms neither has ever been
  committed. Verify with `git check-ignore -v` if you ever move them.
- Do not paste the password into a chat, an issue, or a CI log.
- Do not email the `.jks` to yourself in plain text.
- File permissions are `600` (owner read/write only). If you copy the file,
  check the copy is not world-readable — `cp` does not always preserve this.

## Rebuilding a signed release

```sh
cd "$HOME/Developer/DengueWatchBD android"
./gradlew bundleRelease      # -> app/build/outputs/bundle/release/app-release.aab
./gradlew assembleRelease    # -> per-ABI signed APKs
```

Signing is automatic while `keystore.properties` exists and points at a file
that is present. If it is missing the build still succeeds but emits `-unsigned`
artifacts — so if you see that suffix, the key was not found.
