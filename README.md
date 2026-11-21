# Smart Home (Android, Kotlin + Jetpack Compose)

Native Android app for the CSE322 "Home Appliances Control" project.
Talks to the TM4C123 board through an HC-05 (classic Bluetooth SPP, 9600 baud).

## Open and run
1. Install Android Studio (Koala or newer).
2. File > Open > choose this `SmartHome` folder. Wait for Gradle sync (needs internet the first time).
3. Plug in an Android phone (USB debugging on) or use the "Run" button with a phone selected.
   The emulator cannot use Bluetooth hardware, use a real phone.
4. Pair the HC-05 once in Android Settings > Bluetooth (PIN 1234 or 0000).
5. Run the app, tap Connect, pick the HC-05.

## Protocol used (matches the project handout)
App -> board : LAMP:1 / LAMP:0, PLUG:1 / PLUG:0, LIMIT:30, STATUS?   (each ends with \n)
Board -> app : TEMP:27.5, DOOR:OPEN / DOOR:CLOSED, ALARM:1 / ALARM:0, ACK:<command>

STATUS? replies are accepted in the same formats (LAMP:1, PLUG:0, DOOR:CLOSED, TEMP:27.5, ALARM:0), one per line.

## Behaviour
* Lamp / Plug tiles change only when the board's ACK (or a LAMP:/PLUG: line) arrives, so they always show the real state.
* Door events are logged with the PHONE's clock and saved permanently (SharedPreferences). The first door value after connecting is not logged as an event.
* Temperature graph shows the last 60 readings and the limit line.
* Alarm banner pulses red while ALARM:1 is active.
* Light and dark mode follow the phone setting.

## Files
app/src/main/java/com/example/smarthome/
  BluetoothSerial.kt      RFCOMM socket, line splitting, send
  SmartHomeViewModel.kt   state, protocol parsing, door log storage
  Ui.kt                   all screens and cards (Compose)
  Theme.kt, MainActivity.kt

## Test without the board
Use "Serial Bluetooth Terminal" on a second phone paired with... or simply type lines from a PC terminal into the HC-05
(for example TEMP:27.5, DOOR:OPEN, ALARM:1) and watch the app react.

## Build the APK online (GitHub Actions, no Android Studio needed)
1. Create a free GitHub account and a new repository.
2. Upload the CONTENTS of this folder to the repository root (so `app/`, `build.gradle.kts` and `.github/` are at the top).
3. Open the repository > Actions tab > "Build APK" > wait about 5-10 minutes for the green tick.
4. Click the finished run > Artifacts > download "SmartHome-apk" (a zip containing app-debug.apk).
5. Send app-debug.apk to your phone and install it (allow "install unknown apps").
