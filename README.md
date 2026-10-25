# PhoneBot

Personal Android 15 automation app. Flow 1 posts OnePublish drafts in TikTok. Spec: [docs/](docs/).

TikTok does not push the OnePublish draft notification; it only appears in-app. So the bot checks on
a timer (default every 30 min): TikTok → Inbox → System notifications → TikTok Platform filter →
newest "Your content from OnePublish is ready" item not yet posted → Next → Post.

## Build and install

```sh
export JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home"
./gradlew installDebug        # builds and installs over USB (adb)
```

Or open this folder in Android Studio and press Run. Always install over ADB/Studio, not by opening the APK on the phone (Android 13+ restricted settings).

## Useful ADB commands

```sh
adb logcat -s PhoneBot                                   # bot log
adb shell am broadcast -a dev.malik.phonebot.DUMP        # dump the current TikTok screen tree
adb pull /sdcard/Android/data/dev.malik.phonebot/files/dumps/
adb push selectors.json /sdcard/Android/data/dev.malik.phonebot/files/selectors.json
```

## selectors.json

Optional; built-in defaults are used when absent.

```json
{
  "packages": ["com.zhiliaoapp.musically", "com.ss.android.ugc.trill"],
  "itemRegex": "^Your content from OnePublish is ready"
}
```
