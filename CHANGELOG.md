# Change Log
All notable changes to this project will be documented in this file.

The format is based on [Keep a Changelog](http://keepachangelog.com/)
and this project adheres to [Semantic Versioning](http://semver.org/).

## [Unreleased]

## 0.0.9 – 2019-03-01
### Added
- translations for fastlane descriptions

### Changed
- use another api entry to get map positions, now uses login creds, now shows all sessions
[#38](https://gitlab.com/eneiluj/phonetrack-android/issues/38) @olivier.revelin
- send multiple positions by bunch of 200
[#43](https://gitlab.com/eneiluj/phonetrack-android/issues/43) @florom
- get rid of butterknife
- bump to androidx
- update cert4android
- CI : keep debug apk only

### Fixed

## 0.0.8 – 2019-02-02
### Added
- option to choose app primary color

### Fixed
- crash when getting new sessions while having ones from <=0.0.6
- crash when stopping logginService without calling startForegroundService on Android>=8
- make all UI strings translatable
[#23](https://gitlab.com/eneiluj/phonetrack-android/issues/23) @Valdnet
- avoid loading animation when synced automatically launched
[#31](https://gitlab.com/eneiluj/phonetrack-android/issues/31) @Valdnet
- put units in map popups
[#32](https://gitlab.com/eneiluj/phonetrack-android/issues/32) @Valdnet
- avoid double session sync when getting back from settings
[#28](https://gitlab.com/eneiluj/phonetrack-android/issues/28) @Valdnet
- change session order in UI
[#24](https://gitlab.com/eneiluj/phonetrack-android/issues/24) @Valdnet

## 0.0.7 – 2019-01-28
### Added
- new map feature : watch a session's devices on a map (works with public sessions only)
- map features : show my position, follow me, autozoom, zoom on markers, change tile provider
- able to load tiles from local mapsforge v4 files in /osmdroid/\*.map
- lots of translations (thank you guys)

### Changed
- send multiple point in one request if more than 5 points
- get rid of useless class CloudSession
- less margins in drawer menus
- DBLocation now includes User-agent
- replace URL by link or address in strings
- unified theme accross the app parts

### Fixed
- logjob edition session selection : only show my sessions (not the shared ones)
- get rid of float, now using double
- use cert4android in webTrackService
[#11](https://gitlab.com/eneiluj/phonetrack-android/issues/11) @temrix
- don't update sessions if not necessary when syncing
- fix some icon's color for old android versions
- fix permission requests
- fix Android 6.0 problem with theme changing
[#5](https://gitlab.com/eneiluj/phonetrack-android/issues/5) @Valdnet
- fix info dialog which was not using an up-to-date logjob
[#13](https://gitlab.com/eneiluj/phonetrack-android/issues/13) @Valdnet
- wait 5 seconds when connectivity is back before launching syncService
- remove useless ok button for list select dialog
- map setFrequency field forced to number

## 0.0.6 – 2019-01-11
### Added
- add logjob option to keep gps on between fixes
[#8](https://gitlab.com/eneiluj/phonetrack-android/issues/8) @roydenyates

### Changed
- change behaviour of logjob edit : back=cancel, menuSaveIcon=save
[#6](https://gitlab.com/eneiluj/phonetrack-android/issues/6) @Tobiasff3200
- show session selection dialog when creating phonetrack logjob
[#8](https://gitlab.com/eneiluj/phonetrack-android/issues/8) @roydenyates
- removed splashscreen

### Fixed
- no more double sync on startup
[#5](https://gitlab.com/eneiluj/phonetrack-android/issues/5) @Valdnet

## 0.0.5 – 2019-01-09
### Added
- compatibility with API>=16
[#5](https://gitlab.com/eneiluj/phonetrack-android/issues/5) @Valdnet
- log job fields restrictions

### Changed
- if app is running, launch position sync when network becomes available
- use takisoft fixed preferences
- link to F-Droid in README

### Fixed
- update deprecated network change tracking

## 0.0.4 – 2019-01-05
### Fixed
- color of dialogs buttons
- logjob info dialog
- icon in f-droid
- generate mipmap png for api<=25

## 0.0.3 – 2018-12-19
### Added
- in PT log job : share -> create public share on server and get share URL

### Changed
- improve PT logging URL parsing

## 0.0.2 – 2018-12-14
### Added
- info dialog for log jobs
- translations
- fastlane app descriptions (en and fr)

### Changed
- design improvement

## 0.0.1 – 2018-12-12
### Added
- new app !

### Fixed
- the world
