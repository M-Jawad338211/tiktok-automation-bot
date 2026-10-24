# PhoneBot

Personal Android 15 automation app. Flow 1 posts OnePublish drafts in TikTok. Spec: [docs/](docs/).

## Build and install

```sh
export JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home"
./gradlew installDebug        # builds and installs over USB (adb)
```

Or open this folder in Android Studio and press Run. Always install over ADB/Studio, not by opening the APK on the phone (Android 13+ restricted settings).

## Useful ADB commands

```sh
adb logcat -s PhoneBot                                   # bot log, incl. every TikTok notification's title/text
adb shell am broadcast -a dev.malik.phonebot.DUMP        # dump the current TikTok screen tree
adb pull /sdcard/Android/data/dev.malik.phonebot/files/dumps/
adb push selectors.json /sdcard/Android/data/dev.malik.phonebot/files/selectors.json
```

Fake trigger for testing (posted by `com.android.shell`, so add that package to `trigger.packages` in selectors.json first):

```sh
adb shell cmd notification post -t "Your content from OnePublish is ready" test1 "Edit your video before sharing to TikTok"
```

## selectors.json

Optional; built-in defaults are used when absent.

```json
{
  "trigger": {
    "packages": ["com.zhiliaoapp.musically", "com.ss.android.ugc.trill"],
    "titleRegex": "^Your content from OnePublish is ready",
    "textRegex": "^Edit your video before sharing"
  }
}
```
