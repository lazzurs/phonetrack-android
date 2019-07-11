# Change Log
All notable changes to this project will be documented in this file.

The format is based on [Keep a Changelog](http://keepachangelog.com/)
and this project adheres to [Semantic Versioning](http://semver.org/).

## [Unreleased]
### Added
- new logjob mode: only log if there was a significant move
[#76](https://gitlab.com/eneiluj/phonetrack-android/issues/76) @creywood
[!3](https://gitlab.com/eneiluj/phonetrack-android/merge_requests/3) @creywood
- accuracy circle
[#61](https://gitlab.com/eneiluj/phonetrack-android/issues/61) @creywood
[!4](https://gitlab.com/eneiluj/phonetrack-android/merge_requests/4) @creywood
- now able to receive commands by SMS
(send back location, alarm, startlogjobs, stoplogjobs, createlogjob)
[#17](https://gitlab.com/eneiluj/phonetrack-android/issues/17) @ShareTheKnowledge
- map shortcuts
[!5](https://gitlab.com/eneiluj/phonetrack-android/merge_requests/5) @creywood
- option to get server color
- Nextcloud Maps logjob (not visible for the moment)

### Changed
- format distance in info dialog
[#88](https://gitlab.com/eneiluj/phonetrack-android/issues/88) @markussvn
- enable/disable ability to refresh logjob list layout depending on network availability
- organize settings with categories
- update libs and cert4android
- cleaner logs
- improve map buttons, transparent background

### Fixed
- SSO is now working with all Android versions
- bug with EditTextPreference after theme change
- mapsforge is available right after accepting storage permission
[#102](https://gitlab.com/eneiluj/phonetrack-android/issues/102) @Valdnet
- use local icons instead of system ones
[!6](https://gitlab.com/eneiluj/phonetrack-android/merge_requests/6) @AndyScherzinger
- cert4android crash when accepting certificate

## 0.0.11 – 2019-05-14
### Added
- web login
- SSO login (only on Android 9, 8 and 4 for the moment)
- get device colors from server in map view
[#58](https://gitlab.com/eneiluj/phonetrack-android/issues/58) @markussvn
- ability to create a remote session
- more information/stats in info dialog
- option to reset logjob stats when activated
- map button on logjob list item
[#72](https://gitlab.com/eneiluj/phonetrack-android/issues/72) @markussvn
- option to reduce notification importance
[#64](https://gitlab.com/eneiluj/phonetrack-android/issues/64) @wiktor-k

### Changed
- make dark theme really black
[#53](https://gitlab.com/eneiluj/phonetrack-android/issues/53) @mapcar
- show complete date if now today in map view
[#57](https://gitlab.com/eneiluj/phonetrack-android/issues/57) @markussvn
- use the only session for new PT log jobs if there is only one
[#59](https://gitlab.com/eneiluj/phonetrack-android/issues/59) @markussvn
- update gradle version
- set notification channel
- improved network check
- sync sessions when cert4android gets ready even if it's redundant
- use phone model name as default log job device name
- avoid slashes in log URL, replaced by dash
[#46](https://gitlab.com/eneiluj/phonetrack-android/issues/46) @syntron
- bring marker to front when selected in side menu
[#71](https://gitlab.com/eneiluj/phonetrack-android/issues/71) @markussvn
- if there is only one session, use it for map view
[#59](https://gitlab.com/eneiluj/phonetrack-android/issues/59) @markussvn
[#73](https://gitlab.com/eneiluj/phonetrack-android/issues/73) @markussvn
- when asking for map view, remember last selected session and preselect it
[#59](https://gitlab.com/eneiluj/phonetrack-android/issues/59) @markussvn
[#73](https://gitlab.com/eneiluj/phonetrack-android/issues/73) @markussvn
- session title in map action bar
- marker z-index order by timestamp
[#71](https://gitlab.com/eneiluj/phonetrack-android/issues/71) @markussvn
- improve some icons

### Fixed
- reset last sync error when sync succeeds
[#62](https://gitlab.com/eneiluj/phonetrack-android/issues/62) @markussvn
- sort device name in map sidebar
[#57](https://gitlab.com/eneiluj/phonetrack-android/issues/57) @markussvn
- keyboard behaviour in logjob edition and map
[#63](https://gitlab.com/eneiluj/phonetrack-android/issues/63) @Valdnet
- logger service starts even if OS location is disabled
[#70](https://gitlab.com/eneiluj/phonetrack-android/issues/70) @markussvn
- pressing back in settings now applies app color correctly
[#34](https://gitlab.com/eneiluj/phonetrack-android/issues/34) @Valdnet
- apply logjob minimum accuracy change while running
[!1](https://gitlab.com/eneiluj/phonetrack-android/merge_requests/1) @creywood

## 0.0.10 – 2019-03-11
### Changed
- allow min distance == 0
[#48](https://gitlab.com/eneiluj/phonetrack-android/issues/48) @makuser
- add two flavors, normal and dev, just changes the app icon and ID to install both side by side

### Fixed
- fix speed display in map, convert m/s to km/h
[#49](https://gitlab.com/eneiluj/phonetrack-android/issues/49) @Valdnet

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
