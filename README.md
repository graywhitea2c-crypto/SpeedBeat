# SpeedBeat — GPS Layer Mixer (Android)

SpeedBeat is a Kotlin + Jetpack Compose Android prototype that uses the supplied Phonk and EDM WAV samples as loopable audio layers.

## Included features
- Requests foreground location permission on first launch and provides a GPS connection button.
- Displays GPS speed in km/h.
- Randomly chooses **Phonk** or **EDM** when the app opens. The category can also be selected manually or randomized again from the UI.
- Adds one more simultaneous audio layer each time speed reaches the configured increment. Default: every 10 km/h; configurable from 5 to 30 km/h.
- Manual BPM slider (60–180 BPM).
- Optional GPS-linked BPM mode.
- Master volume control.
- Includes the supplied WAV samples under `app/src/main/res/raw/`.

## Layer rule
`active layers = floor(speed km/h / configured increment) + 1`, capped to the number of samples in the selected category. For example, with a 10 km/h increment: below 10 km/h = 1 layer, at 10 = 2, at 20 = 3, at 30 = 4, etc. The app uses the current GPS speed, not total distance travelled.

## Sample categories
- **Phonk:** `phonk_01.wav` … `phonk_04.wav`
- **EDM:** `edm_01.wav` … `edm_06.wav`

At each layer increase, the app selects a sample not already used in the current stack when possible. Each active sample loops until its layer is removed or playback is stopped.

## Open and build
1. Extract the ZIP.
2. Open the `SpeedBeat` folder in Android Studio.
3. Use JDK 17 and allow Gradle Sync to finish.
4. Run on an Android phone (min SDK 26 / Android 8.0).
5. Accept location permission. If the system permission dialog no longer appears, open App info → Permissions → Location and allow it.
6. Enable Location on the phone and test outdoors for a reliable GPS fix.

## Important notes
- This is a source project ZIP, not a prebuilt APK. It has not been compiled or tested on a physical Android device in this environment.
- Audio samples are looped as supplied. Different clips may have different tempos/loop lengths, so perfect beat-phase synchronization is not guaranteed yet. The BPM control changes playback speed and therefore may also change pitch; tempo-preserving time-stretching and beat-grid analysis would be a future audio-engine upgrade.
- GPS speed can fluctuate and may be unavailable indoors. Do not use the app while driving if interacting with its controls would distract you.
