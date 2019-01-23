# PhoneTrack for Android
An android app to log locations to [PhoneTrack Nextcloud app](https://gitlab.com/eneiluj/phonetrack-oc) or any custom server.

[![Crowdin](https://d322cqt584bo4o.cloudfront.net/phonetrack/localized.svg)](https://crowdin.com/project/phonetrack)

This is still a beta version.

This app is originally a fork of [Nextcloud Notes for Android](https://github.com/stefan-niedermann/nextcloud-notes).
It is also largely insired by [µLogger for Android](https://github.com/bfabiszewski/ulogger-android).
Many thanks to their developers :heart: !

What's different from other logging apps ?

PhoneTrack Android app can manage multiple configurations (called log jobs)
and log locations at different frequencies to different destinations in parallel.
This means you can simultaneously have :

* a low frequency log job which logs one point per hour (for example) to keep track of where you go
* a high frequency log job which logs one point every five seconds (for example) to record a hike and potentially share it for others to be able to see your position in real time
* a medium frequency log job which logs one point every five minutes (for example) to share with your friends to help them finding you if you got separated during a city walk

Log jobs are independent and can be individually activated.

It can log to [PhoneTrack Nextcloud app](https://gitlab.com/eneiluj/phonetrack-oc) with a `PhoneTrack log job`
or to any server which can receive HTTP GET or POST requests with a `custom log job`.

## User documentation

[Over there](https://gitlab.com/eneiluj/phonetrack-android/wikis/userdoc)

## Install

* APK Direct download : [releases in project wiki](https://gitlab.com/eneiluj/phonetrack-android/wikis/home#releases)
* [![PhoneTrack App on fdroid.org](https://gitlab.com/eneiluj/phonetrack-android/wikis/uploads/57bb389a0c40f5cb81dc1ae21a314adb/fd.png)](https://f-droid.org/packages/net.eneiluj.nextcloud.phonetrack/)

## Build

If you want to build this app yourself, clone this repository :

``` bash
git clone --recurse-submodules https://gitlab.com/eneiluj/phonetrack-android
```

or download [master branch latest archive](https://gitlab.com/eneiluj/phonetrack-android/-/archive/master/phonetrack-android-master.zip).

Then open/import the project in Android studio and build it.

## Donate

* [Donate with Liberapay : ![Donate with Liberapay](https://liberapay.com/assets/widgets/donate.svg)](https://liberapay.com/eneiluj/donate)
* [Donate with Paypal : <img src="https://gitlab.com/eneiluj/phonetrack-android/wikis/uploads/3ef4665a2c25662265681d6304f71b43/paypal-donate-button.png" width="80"/>](https://www.paypal.com/cgi-bin/webscr?cmd=_s-xclick&hosted_button_id=66PALMY8SF5JE)


## :eyes: Screenshots


## Features
* Create multiple log jobs to run in the background to log positions to different places
* Send locations to PhoneTrack Nextcloud app
* Send locations to custom server with GET or POST requests
* Store positions when network is not available
* Launch on system startup
* Theming
* Translated in many languages on [Crowdin](https://crowdin.com/project/phonetrack)


## :link: Requirements
* Android >= 4.1
* [Nextcloud](https://nextcloud.com/) instance running
* [Nextcloud PhoneTrack](https://gitlab.com/eneiluj/phonetrack-oc) app enabled

## :notebook: License
This project is licensed under the [GNU GENERAL PUBLIC LICENSE](/LICENSE).

## :twisted_rightwards_arrows: Alternatives

You can log to PhoneTrack with other Android apps (and one IOS one). Check out the [list of compatible loggers in PhoneTrack wiki](https://gitlab.com/eneiluj/phonetrack-oc/wikis/userdoc#logging-methods)
