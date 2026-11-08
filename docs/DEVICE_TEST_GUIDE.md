# Device test guide — Jiàndié Camera on the Realme 6

Latest CI build with the camera app: https://github.com/bwjremployment-bit/baby-monitor/actions/runs/42449577463
(scroll to **Artifacts**, download **jiandie-camera-debug**, unzip, copy the `.apk` to the phone, tap it, allow "install unknown apps".)

These checks need **no server and no viewer**. Do them with the phone **plugged in**.

## Test A — can standby use the camera and microphone with the screen off? (T-005, the critical one)
1. Open Jiàndié Camera. Allow the **Camera** permission. You should see the camera preview and a pill at the top saying **Not transmitting**.
2. Tap the gear (bottom right) to open **Radius**.
3. Under **Server**, type any address that starts with `wss://`, for example `wss://example.invalid`, and tap **Save**. (The server does not need to exist for this test; the app will just say it can't reach it.)
4. Tap **Turn on standby**. Allow **Camera**, **Microphone** and **Notifications** if asked. A notification appears: *"Jiàndié Camera — standby … camera and microphone are off"*. Status shows *Server: not connected* — expected.
5. Scroll to **Keep it running** and tap **Background self-test (30 s)**.
6. **Press the power button to lock the phone.** Leave it locked for about **40 seconds**.
7. Unlock. Open the app → Radius → scroll to **Diagnostics**. Find the line starting **"Self-test result"**.
8. Tap **Copy log** and paste it to me, or just tell me the camera and microphone results.

What the results mean:
- *camera: OK, microphone: OK* → the design works on your phone. 
- *camera: FAILED* or *microphone: SILENT/FAILED* → Android or Realme's battery manager is blocking background capture. Don't worry — I have fallbacks, and this is exactly what the test is for.

## Test B — does it survive being forgotten? (T-005c)
Turn on standby, lock the phone, leave it on the charger overnight. In the morning, is the notification still there? Tell me yes/no. If no, we'll adjust the Realme battery settings (Radius → Keep it running).

## Test C — resume after restart (T-005b)
With standby on, restart the phone. After it boots you should get a quiet notification **"Standby stopped — tap to open…"**. Tap it: the app should open and standby turn back on.

## Also useful to tell me
- Does the preview look right? Does **flip camera** work? Does the **flash** button appear and work?
- Radius → **Pair with Yps Viewer**: does a QR code and a 28-character code show?
