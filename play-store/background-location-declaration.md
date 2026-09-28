# Play Console — background location declaration

Play reviews `ACCESS_BACKGROUND_LOCATION` individually and usually asks for a
video. This is the text to adapt; the video is yours to record.

## Which feature requires background location

One feature only: **"Warn me when I enter a high-risk area"**, on the Alerts
screen (Prevent tab → Alert me).

## Declaration text

> DengueWatch BD is a public-health app for dengue surveillance in Bangladesh.
> It uses background location for a single feature: warning a user when they
> enter an area that DGHS surveillance data places in the high or severe dengue
> risk band, so they can take precautions such as using repellent.
>
> This requires background access because the warning is only useful if it can
> reach the user while they are travelling with the app closed. Restricted to
> foreground access, the app could only warn someone who was already looking at
> it, which would not tell them anything they had not just read.
>
> The permission is optional and off by default. The app is fully usable
> without it: a user can select their area manually and see the same risk
> information. The permission is requested only after the user switches the
> warning on, never at first launch.
>
> Location is processed entirely on the device. It is not transmitted to any
> server, not stored off-device, and not shared with any third party. The app
> has no backend, no account system, and no analytics.

## What the video must show

Play wants the in-app journey that leads to the permission prompt:

1. Open the app, go to the **Prevent** tab.
2. Scroll to and open **Alert me**.
3. Show the section **"Alerts where you are"** and its explanation:
   *"Uses your location in the background to notice when you cross into an area
   in the high or severe band. Your location is checked on this phone and is
   never uploaded."*
4. Switch on **"Warn me when I enter a high-risk area"**.
5. Show the resulting Android permission request.
6. Show the status afterwards — *"On, including in the background"* and
   *"Watching N high-risk areas"*.

Record it on a real device with the release build. Narration is not required.

## Consistency check

The in-app text, this declaration and the privacy policy must not contradict
each other. All three currently say the same thing: on-device only, optional,
never uploaded. If you change one, change all three.
