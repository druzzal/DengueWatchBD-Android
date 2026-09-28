# Play Store submission pack

Everything here is drafted from the app's actual behaviour, verified by audit.
Nothing is invented; every gap is marked FILL IN.

| File | What it is for |
|---|---|
| `privacy-policy.md` | The policy text. Publish it at a public URL and give Play that URL. |
| `store-listing.md` | App name, descriptions, category, rating notes, asset checklist. |
| `data-safety.md` | Answers for the Data Safety form, with the code evidence behind each. |
| `background-location-declaration.md` | Declaration text and the video script Play will ask for. |
| `screenshots/` | Five phone screenshots, 1080×2340, captured on a real device. |

## Hosting the privacy policy

You already publish GitHub Pages at `druzzal.github.io/dengue-bd-dashboard`,
which is the least-effort place to put it. Add the policy as `privacy.md` (or
`privacy.html`) in that repository and it will be served at:

    https://druzzal.github.io/denguewatch-bd-privacy/

That URL goes in two places: the Play Console listing, and — recommended — a
link row in the app's own About screen, under Privacy.

## Order of work

1. Create the signing keystore (see `../SIGNING.md`) and build a signed AAB.
2. Publish the privacy policy and note its URL.
3. Create the app in Play Console; complete the listing from `store-listing.md`.
4. Answer Data Safety from `data-safety.md` and Content Rating.
5. Submit the background-location declaration and its video.
6. Upload the AAB to internal testing first, and install it from Play once to
   confirm the signed artifact works end to end.
7. Closed testing if your account requires it, then production.
