# Change Log
All notable changes to this project will be documented in this file.

The format is based on [Keep a Changelog](http://keepachangelog.com/)
and this project adheres to [Semantic Versioning](http://semver.org/).

## [Unreleased]
## 0.1.0 – 2020-06-25
### Changed
- apply new design (like NC Notes and NC Files)
[#206](https://gitlab.com/eneiluj/phonetrack-android/issues/206) @stefan-niedermann

## 0.0.19 – 2020-06-22
### Added
- ability to import MapsForge files from anywhere and store them in app data
- display device history in map (local+remote data), able to toggle and filter history
[#199](https://gitlab.com/eneiluj/phonetrack-android/issues/199) [#134](https://gitlab.com/eneiluj/phonetrack-android/issues/134) @tgc12 @Valdnet
- option to be able to rotate the map
- export local logjob points to GPX

### Changed
- update OsmDroid to 6.1.6 which fixes display bug and brings MapsForge v5 compat
[#183](https://gitlab.com/eneiluj/phonetrack-android/issues/183) @Valdnet
- improve main sidebar layout
- display NC account avatar in sidebar
- use color gradient everywhere
- remove 'read contacts' permission finally not necessary for SSO
- update SSO lib to 0.5.1

### Fixed
- fix logging with keepGpsOn which was stopped after getting the first point
[#197](https://gitlab.com/eneiluj/phonetrack-android/issues/197) @DavidMndz @huste
- get rid of takisoft stuff and fix some resources to be able to update gradle
- map sidebar display with lots of devices
- keep map sidebar device list scroll state on data update

## 0.0.18 – 2020-04-13
### Added
- system to fix buggy GPS chips time results
[!10](https://gitlab.com/eneiluj/phonetrack-android/-/merge_requests/10) @quite
- logjob config presets (bike, walk, drive, battery, max presicion)
[#184](https://gitlab.com/eneiluj/phonetrack-android/issues/184) @tmannn.24

### Changed
- increase zoom margin in map
[#177](https://gitlab.com/eneiluj/phonetrack-android/issues/177) @huste
- improve LoggerService, drop requestSingleUpdate, use requestLocationUpdates
[#179](https://gitlab.com/eneiluj/phonetrack-android/issues/179) @jgoerzen @roydenyates
- remove passive location provider which was not properly used

### Fixed
- min accuracy field was hidden with sigMotion mode enabled
[#172](https://gitlab.com/eneiluj/phonetrack-android/issues/172) @manuquadrat
- multiple fixes in logjob edition form validity checking

## 0.0.17 – 2020-01-26
### Added

### Changed
- rewrote logger service to radically improve battery life by making it possible to add timeouts
[#132](https://gitlab.com/eneiluj/phonetrack-android/issues/132) @sserkez @Simon\_Garfunkel @Shamp0o @larsfp
- min Android version 4.1 => 4.3
- big improvements around SMSs (timeouts, error responses)
- allow to use and log to session shared with 'me'
[#103](https://gitlab.com/eneiluj/phonetrack-android/issues/103) @markussvn

### Fixed
- now possible to log in if PhoneTrack is not installed on Nextcloud instance
[#160](https://gitlab.com/eneiluj/phonetrack-android/issues/160) @ghost1
- multiple sms listeners running for same sender
- URL validator in settings
[#166](https://gitlab.com/eneiluj/phonetrack-android/issues/166) @huste

## 0.0.16 – 2020-01-03
### Changed
- new launcher icon background
[!8](https://gitlab.com/eneiluj/phonetrack-android/merge_requests/8) @stefan-niedermann

## 0.0.15 – 2019-12-29
### Fixed
- significant motion sensor presence check
- bug when using big numbers for point grouping
[#155](https://gitlab.com/eneiluj/phonetrack-android/issues/155) @Valdnet

## 0.0.14 – 2019-12-29
### Added
- start/stop SMS commands to toggle a specific logjob
- add login/password fields to custom logjobs (basic HTTP auth)
[#144](https://gitlab.com/eneiluj/phonetrack-android/issues/144) @TRS-80
- custom logjobs now able to send JSON payload
[#144](https://gitlab.com/eneiluj/phonetrack-android/issues/144) @TRS-80
- compatibility with OpenHab
[#144](https://gitlab.com/eneiluj/phonetrack-android/issues/144) @TRS-80
- new options to respect power saving mode and offline/airplane mode
[#153](https://gitlab.com/eneiluj/phonetrack-android/issues/153) @amo13
- new option to group point sending to reduce data usage
[#151](https://gitlab.com/eneiluj/phonetrack-android/issues/151) @jangop

### Changed
- bump SSO lib to 0.4.1, working with Nextcloud Files dev
[#140](https://gitlab.com/eneiluj/phonetrack-android/issues/140) @jancborchardt
- adapt background location permission to Android 10 (Q, sdk 29)
- remember last used logjob settings for new ones
[#152](https://gitlab.com/eneiluj/phonetrack-android/issues/152) @florom

### Fixed
- get color when it's compacted
- session creation with special characters
[#136](https://gitlab.com/eneiluj/phonetrack-android/issues/136) @florom
- URLencode GET parameters

## 0.0.13 – 2019-09-29
### Added
- notification when SMS position is sent
- signed release apk production when pushing to release branch
- significant motion mixed mode to record on every motion and also with an interval
[#109](https://gitlab.com/eneiluj/phonetrack-android/issues/109) @MarkLi
- passive location provider setting
[#112](https://gitlab.com/eneiluj/phonetrack-android/issues/112) @helium314
- crosshair under logjob switch, helps to know if at least a point was logged since last activation
[#108](https://gitlab.com/eneiluj/phonetrack-android/issues/108) @markussvn
- new logjob type to log to Nextcloud Maps!

### Changed
- improve CI script
- use minimum distance in significant motion mode
[#109](https://gitlab.com/eneiluj/phonetrack-android/issues/109) @MarkLi
- refresh info dialog on resume and when new point is logged
[#113](https://gitlab.com/eneiluj/phonetrack-android/issues/113) @markussvn
- ask deletion confirmation when trying to delete active logjob
[#114](https://gitlab.com/eneiluj/phonetrack-android/issues/114) @markussvn
- change duration format to hh:mm:ss
[#108](https://gitlab.com/eneiluj/phonetrack-android/issues/108) @markussvn
- logjob edition is now a form

### Fixed
- LoggerService crash on Android <= 4.3 because of unsafe signMotion disabling
[#111](https://gitlab.com/eneiluj/phonetrack-android/issues/111) @florom
- update username in drawer when resuming
- clear sessions just after disabling SSO, update logjob items to make map icon appear/disappear
[#87](https://gitlab.com/eneiluj/phonetrack-android/issues/87) @Valdnet
- bad device name encoding (crash with spaces) on Android <= 6
[#83](https://gitlab.com/eneiluj/phonetrack-android/issues/83) @GURKE
- logjob item buttons alignment
[#115](https://gitlab.com/eneiluj/phonetrack-android/issues/115) @Valdnet
- bug when going to the map without selecting a session
[#116](https://gitlab.com/eneiluj/phonetrack-android/issues/116) @Valdnet
- bug when setting invalid frequency value in map
[#117](https://gitlab.com/eneiluj/phonetrack-android/issues/117) @Valdnet

## 0.0.12 – 2019-07-25
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
