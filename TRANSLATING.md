# Translating PhoneTrack

Thank you for helping. This guide is for someone who wants to translate the app and has never
built it. You need a GitHub account; you do not need Android Studio.

Translations of this fork are contributed by **pull request** on GitHub. There is no
translation platform: the Crowdin project mentioned in older text belongs to the
[original app](https://gitlab.com/eneiluj/phonetrack-android) and does not contain the strings
this fork added.

## Where things stand

- `app/src/main/res/values/strings.xml` is the English text, the source for every language.
- Each language has a folder next to it: `values-de-rDE` (German), `values-fr-rFR` (French),
  `values-pt-rBR` (Brazilian Portuguese) and so on. There are 46 of them, plus two English
  variants (`values-en-rGB`, `values-en-rUS`).
- **None of them has the strings this fork added**, for example the SMS allowed senders, the
  SMS alarm notification, the upload error messages and the permission explanations. Until
  they are translated, the app shows them in English.
- Many language files came from the original app's Crowdin export, which filled in English
  wherever nobody had translated a string. So a file can exist and still be mostly English.

## What to translate, and what not

Translate the text between `<string name="...">` and `</string>`, and the text of each
`<item>` in a `<plurals>`. Never change the `name="..."`.

Do **not** translate:

- strings marked `translatable="false"` in `values/strings.xml` (settings keys, web
  addresses, names, the `#RRGGBB` colour hint). Leave them out of your file entirely.
- placeholders: `%s`, `%d`, `%1$s`, `%2$d`, `%.2f` and `%%` (a literal percent sign).
  Keep every placeholder of the English text, exactly as written. You may move them
  within the sentence; if a string has more than one, use the numbered form
  (`%1$s`, `%2$d`) so that the order can change.
- HTML in a few About screen strings, such as `&lt;a href="%1$s">%1$s&lt;/a>`: keep the
  markup and the placeholder, translate only the words around it.
- product names and technical words that are not translated in your language (Nextcloud,
  PhoneTrack, SMS, GPS, GPX).

Special characters:

- write an apostrophe as `\'` (`l\'application`). An apostrophe without the backslash
  stops the app from building. A typographic apostrophe (`’`) needs no backslash.
- write a double quote as `\"`; Android leaves out a plain `"`.
- `\n` is a line break. Keep it where the English has one.
- `...` and `…` both work; follow the habit of your language.

A comment line just above a string (`<!-- ... -->`) explains where it is shown or what a
placeholder stands for. You do not need to copy the comments into your file.

### Plurals

A `<plurals>` has one `<item>` for each grammatical number:

```xml
<plurals name="settings_send_deadline_summary">
    <item quantity="one">%d hour</item>
    <item quantity="other">%d hours</item>
</plurals>
```

English only needs `one` and `other`. Use the quantities your language needs (`zero`,
`one`, `two`, `few`, `many`, `other`), as listed for your language in the
[Unicode plural rules](https://www.unicode.org/cldr/charts/latest/supplemental/language_plural_rules.html).
`other` is always required. A form may leave the number out (`one`: "an hour"), but must not
add a placeholder that the English does not have.

## Find the file for your language

Look in [`app/src/main/res`](app/src/main/res) for a `values-...` folder for your language. If
there is one, edit its `strings.xml` (next section). If not, start a new one.

### Start a new language

1. Find the folder name. Android uses the two-letter
   [ISO 639-1 language code](https://en.wikipedia.org/wiki/List_of_ISO_639_language_codes),
   optionally followed by `-r` and a two-letter region (country) code in capitals:
   - `values-es`: Spanish, for every country
   - `values-pt-rBR`: Portuguese as written in Brazil
   - `values-b+sr+Latn`: Serbian in Latin script (to name a script, use the `b+` form,
     with `+` between the parts)

   Use the language alone (`values-es`) unless the regional variant matters. A phone set to
   a regional variant that the app does not have uses another variant of the same language.
   The full rules are in the
   [Android documentation](https://developer.android.com/guide/topics/resources/providing-resources),
   under "Language, script (optional), and region (optional)".
   Hebrew and Indonesian already have folders under their old codes (`values-iw-rIL`,
   `values-in-rID`): use those.
2. Create `app/src/main/res/values-<code>/strings.xml` with this frame:

   ```xml
   <?xml version="1.0" encoding="utf-8"?>
   <resources>
   </resources>
   ```

3. Copy only the strings you translate into it, between `<resources>` and `</resources>`.
   Do not copy the whole English file: a string that is missing from your file is shown in
   English, which is better than a copy of the English text that looks translated. You do
   not have to translate everything at once; start with the [priority strings](#where-to-start).

The new language appears by itself in the phone's per-app language settings on Android 13
and newer.

### Edit an existing translation

Open the language's `strings.xml`, add the missing strings anywhere between `<resources>`
and `</resources>`, and correct the ones you find wrong. To replace the English text left
by the old Crowdin export, translate the string in place.

### See what is missing

With Python 3 (installed on most Linux and macOS computers; on Windows, from
[python.org](https://www.python.org/downloads/)), from the top folder of the repository:

```bash
python3 scripts/i18n/missing-strings.py de-rDE
python3 scripts/i18n/missing-strings.py --same-as-english de-rDE
```

Use the part of the folder name after `values-` (`de-rDE`, `es`, `b+sr+Latn`). The first
command prints the English strings your file does not have yet, ready to copy into it, with
their explanatory comments. The second also lists the strings that still have the English
text. Without Python, compare your file with `values/strings.xml` by hand: every string
there without `translatable="false"` belongs in your file.

## Where to start

These strings are shown at moments where a misunderstanding matters: they decide who can
control the phone by SMS, and what the person holding a ringing phone reads. Please
translate them first and with the most care.

| What | String names |
|---|---|
| Who may send SMS commands | `settings_sms_allowed_senders_title`, `settings_sms_allowed_senders_long`, `settings_sms_allowed_senders_none`, `settings_sms_summary_no_senders`, `settings_sms_add_contact_title`, `error_invalid_sms_allowed_sender`, `error_no_contact_picker` |
| An SMS command was ignored or run | `sms_ignored_notification_title`, `sms_ignored_notification`, `sms_ignored_notification_no_senders`, `sms_command_notification_title`, `sms_command_notification`, `sms_command_notification_no_pin` |
| The SMS alarm and position request | `sms_alarm_notification`, `sms_alarm_notification_stop` (the Stop button: keep it short), `sms_alarm_notification_channel`, `sms_position_in_progress_notification` |
| Access to a server on the local network (Android 17) | `local_network_permission_title`, `local_network_permission_message` |

## Check your file

### Quick check (Python 3 only)

```bash
python3 scripts/i18n/missing-strings.py de-rDE
```

It stops with a message if the file is not valid XML, and lists the strings whose
placeholders differ from the English ones and the apostrophes or quotes without a
backslash. If it finds a problem, it exits with an error code, and lists the problems
before the missing strings.

### Full check (needs the Android build tools)

The same checks run in CI on every pull request, and those are the ones that count. To run
them on your computer you need JDK 21 and the Android SDK (Android Studio installs both):

```bash
./gradlew testNormalDebugUnitTest --tests '*ResourceFormatStringsTest' --tests '*TranslationCoverageTest'
./gradlew lintNormalDebug
```

`ResourceFormatStringsTest` fails if a translation drops, adds or changes a placeholder, or
uses several placeholders without numbering them; such a string would crash the app or show
the wrong value. `TranslationCoverageTest` never fails because of a missing translation: it
prints, for each language, how many strings are missing and how many are still English. Its
report is in the test results (`app/build/reports/tests/testNormalDebugUnitTest/index.html`,
"Standard output" of that test). Lint checks the rest of the resource files and reports
a plural without the quantities a language needs as a warning.

If your connection goes through a proxy, add
`-Dhttps.proxyHost=<host> -Dhttps.proxyPort=<port>` (and the same with `http.`) to the
Gradle commands.

### See it in the app

Every pull request builds an APK. On the pull request, open **Checks**, then the **Build**
workflow run, and download `phonetrack-dev-debug` under "Artifacts" (you need to be
signed in to GitHub). It is "PhoneTrack Dev", which installs next to the normal app. You
can also build it yourself with `./gradlew assembleDevDebug`
(`app/build/outputs/apk/dev/debug/`). Then set the app's language in the phone's settings
(Android 13 and newer: Settings > System > Languages > App languages > PhoneTrack Dev).

Some things only a phone shows: whether a long translation fits on a button or in a
notification, and how the SMS alarm and SMS notifications read when they arrive. They need
SMS commands from a second phone (see the SMS settings), so check them if you can, and say
in the pull request what you could not check.

## Open the pull request

1. Fork the repository on GitHub, and create a branch named `i18n/<code>`, for example
   `i18n/es` or `i18n/de-rDE`. Without Git, the GitHub web editor does this for you: open
   the language's `strings.xml` on GitHub and click the pencil icon (for a new language,
   open the `app/src/main/res` folder and use **Add file > Create new file**, typing
   `values-<code>/strings.xml` as the name). GitHub makes the fork, and **Propose changes**
   opens the pull request. Its branch is then called `patch-1` or similar, which is fine.
2. Change **one language per pull request**, and only that language's `strings.xml`. Leave
   `values/strings.xml` (English) and the other languages alone; if the English text is
   wrong or unclear, say so in the pull request or
   [open an issue](https://github.com/lazzurs/phonetrack-android/issues).
3. Do not change `appVersionName` or `appVersionCode` in `gradle.properties`; a release
   sets them, and CI rejects a pull request from a fork that changes them.
4. Write the commit message, and the pull request title, as
   [Conventional Commits](https://www.conventionalcommits.org) in English, because the
   release notes are made from them and CI checks them:
   - a new language: `feat(i18n): add Spanish translation`
   - more strings, or corrections, in a language: `fix(i18n): translate the SMS settings into German`
     or `fix(i18n): correct the German wording of the SMS commands`

   (`i18n` on its own is not a type that CI accepts; it goes in brackets after `feat` or
   `fix`.) If you get it wrong, CI says so, and the maintainer can correct it when merging.
5. In the pull request, say which language it is, that you are a fluent speaker, and what
   you could not check on a phone.

## What the reviewer checks

- CI passes: the file is valid, placeholders match, the app builds and lint is clean.
- Only one language's `strings.xml` changed, and no `translatable="false"` string was added.
- Placeholders, `\'`, `\"` and the HTML in the About strings are intact, and plurals have
  the quantities the language needs.
- The words used match the rest of that language's file (for example the word chosen for
  "log job").
- The text was written by a person who speaks the language.

**Machine-translated text is not accepted**: no output from Google Translate, DeepL, a
chatbot or similar, also not "checked afterwards". Some of these strings tell people who
can control their phone by SMS, warn that an SMS command was ignored, and are what someone
reads when the phone starts ringing an alarm. A wrong or misleading word there is a safety
problem, and the maintainer cannot read most languages to catch it. Looking up a single
word in a dictionary is fine; the sentences must be yours. When possible, a second speaker
of the language is asked to read the pull request.

## Credit

You are credited as the author of your commit: the name and e-mail address of your Git (or
GitHub) account appear in the repository history, and the release notes link to every
change. If you prefer a pseudonym, set it as your Git name before you commit. The
"Translators" line on the About screen credits the PhoneTrack community on Crowdin, who
made the original app's translations.

## For developers: changing the English text

- New strings go in `values/strings.xml` only; do not add English copies to the language
  folders, and do not machine-translate (see above).
- If you change the **meaning** of an existing string, give it a new name: every language
  file still has a translation of the old meaning, and Android would keep showing it.
- `values-en-rGB` and `values-en-rUS` contain copies of many English strings, and those
  copies win over `values/strings.xml` on British and US phones. When you change an
  English string that is in them, change it there too, or remove it there.
- A string that is not text (a setting key, an address, a format such as `#RRGGBB`) gets
  `translatable="false"`. Android still uses a language file's copy of such a string if it
  has one, so give it a new name rather than changing its value.
- `TranslationCoverageTest` prints the strings that no language has translated and that are
  not yet in `app/src/test/resources/translations-known-missing.txt`; add them to that
  list. It fails only if the list names a string that is no longer translatable.
