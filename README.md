<h2 align="center">
    <img src="fastlane/metadata/android/en-US/images/icon.png" alt="icon" width="90"/>
    <br />
    <b>NotallyXO | Secure note taking with cloud synchronization</b>
    <p>
        <center>
            <a href='https://play.google.com/store/apps/details?id=com.sleepyyui.notallyxo&pcampaignid=pcampaignidMKT-Other-global-all-co-prtnr-py-PartBadge-Mar2515-1'><img alt='Get it on Google Play' src='https://play.google.com/intl/en_us/badges/static/images/badges/en_badge_web_generic.png' height='80'/></a>
            <a href="https://f-droid.org/en/packages/com.sleepyyui.notallyxo"><img alt='IzzyOnDroid' height='80' src='https://fdroid.gitlab.io/artwork/badge/get-it-on.png' /></a>
            <a href="https://apt.izzysoft.de/fdroid/index/apk/com.sleepyyui.notallyxo"><img alt='F-Droid' height='80' src='https://gitlab.com/IzzyOnDroid/repo/-/raw/master/assets/IzzyOnDroid.png' /></a>
        </center>
    </p>
</h2>

<div style="display: flex; justify-content: space-between; width: 100%;">
  <img src="fastlane/metadata/android/en-US/images/phoneScreenshots/1.png" alt="Image 1" style="width: 32%;"/>
  <img src="fastlane/metadata/android/en-US/images/phoneScreenshots/2.png" alt="Image 2" style="width: 32%;"/>
  <img src="fastlane/metadata/android/en-US/images/phoneScreenshots/3.png" alt="Image 3" style="width: 32%;"/>
</div>

<div style="display: flex; justify-content: space-between; width: 100%;">
  <img src="fastlane/metadata/android/en-US/images/phoneScreenshots/4.png" alt="Image 4" style="width: 32%;"/>
  <img src="fastlane/metadata/android/en-US/images/phoneScreenshots/5.png" alt="Image 5" style="width: 32%;"/>
  <img src="fastlane/metadata/android/en-US/images/phoneScreenshots/7.png" alt="Image 7" style="width: 32%;"/>
</div>

## About NotallyXO

NotallyXO is the secure cloud-enabled version of NotallyX, offering all the features you love from the original app plus seamless synchronization across devices with end-to-end encryption.

### Key Features

#### Cloud Synchronization
* **End-to-End Encryption**: Your data is encrypted before it leaves your device
* **Custom Server Support**: Connect to your own self-hosted server or use our cloud service
* **Real-time Updates**: Changes sync instantly across devices via WebSocket connections
* **Offline Support**: Work without an internet connection and sync when back online
* **Conflict Resolution**: Smart handling of conflicts with manual merge options

#### Note Sharing & Collaboration
* **Secure Note Sharing**: Share notes with other users via one-time hash tokens
* **Access Control**: Set read-only or read-write permissions for shared notes
* **Real-time Collaboration**: See changes from collaborators as they happen

#### Core Note Features
* **Rich Text Support**: Format with bold, italics, monospace and strike-through
* **Task Lists**: Create hierarchical to-do lists with subtasks
* **Media Attachments**: Add images, PDFs, and other file types to your notes
* **Advanced Organization**: Color, pin, and label notes for easy categorization
* **Reminders**: Set time-based notifications for important notes
* **Link Detection**: Automatic detection of URLs, phone numbers, and email addresses
* **Undo/Redo**: Full history of changes with undo/redo support
* **Home Screen Widgets**: Quick access to important notes
* **Security**: Lock notes with biometric authentication or PIN
* **Automatic Backups**: Scheduled backups to protect your data

#### UI & Experience
* **Multiple View Options**: Display notes in list or grid layout
* **Quick Share**: Easily share notes as text
* **Quick Audio Notes**: Create audio notes with a single tap
* **Customization**: Extensive preferences to adjust the app to your liking
* **Task Management**: Actions to quickly manage completed tasks
* **Adaptive Icon**: Modern adaptive app icon

---

### Self-Hosting

NotallyXO allows you to use your own server for synchronization:

1. Set up the backend server using our [self-hosting guide](/backend/SELF_HOSTING.md)
2. Configure the app to use your custom server in Settings
3. Enjoy complete control over your data

---

### Bug Reports / Feature Requests
If you find any bugs or want to propose a new feature, please [create a new Issue](https://github.com/SleepyYui/NotallyXO/issues/new/choose)

When the app crashes, you'll see a dialog that allows you to create a bug report on GitHub with pre-filled crash details.

#### Beta Releases

We regularly release beta versions of the app to gather feedback before public releases.
Beta releases use a separate `applicationId`, appearing on your device as `NotallyXO BETA` with their own data storage.
You can download the most recent beta release [here on GitHub](https://github.com/SleepyYui/NotallyXO/releases/tag/beta)

### Translations
All translations are crowd sourced.
To contribute:
1. Download current [translations.xlsx](https://github.com/SleepyYui/NotallyXO/raw/refs/heads/main/app/translations.xlsx)
2. Open in Excel/LibreOffice and add missing translations
   Notes:
   - Missing translations are marked in red
   - You can filter by key or any language column values
   - Non-Translatable strings are hidden and marked in gray, do not add translations for them
   - For plurals, some languages need/have more quantity strings than others, if a quantity string in the default language (english) is not needed the row is highlighted in yellow. If your language does not need that quantity string either, ignore them.
3. Open a [Update Translations Issue](https://github.com/SleepyYui/NotallyXO/issues/new?assignees=&labels=translations&projects=&template=translation.md&title=%3CINSERT+LANGUAGE+HERE%3E+translations+update)
4. We will create a Pull-Request to add your updated translations

See [Android Translations Converter](https://github.com/PhilKes/android-translations-converter-plugin) for more details

### Contributing

If you would like to contribute code, just grab any open issue (that has no other developer assigned yet), leave a comment that you want to work on it and start developing by forking this repo.

The project is a standard Android project written in Kotlin, and we recommend using Android Studio for development. Test your changes with an Android device/emulator that uses the same Android SDK Version as defined in the `build.gradle` `targetSdk`.

Before submitting your proposed changes as a Pull-Request, make sure all tests are still working (`./gradlew test`), and run `./gradlew ktfmtFormat` for common formatting (also executed automatically as pre-commit hook).

### Attribution
The original Notally project was developed by [OmGodse](https://github.com/OmGodse) under the [GPL 3.0 License](https://github.com/OmGodse/Notally/blob/master/LICENSE.md).

NotallyX was developed by [PhilKes](https://github.com/PhilKes) as an extended version.

NotallyXO is maintained by [SleepyYui](https://github.com/SleepyYui) with cloud synchronization capabilities.

In accordance with GPL 3.0, this project is licensed under the same [GPL 3.0 License](https://github.com/SleepyYui/NotallyXO/blob/master/LICENSE.md).
