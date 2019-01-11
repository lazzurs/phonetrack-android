# Change Log
All notable changes to this project will be documented in this file.

The format is based on [Keep a Changelog](http://keepachangelog.com/)
and this project adheres to [Semantic Versioning](http://semver.org/).

## [Unreleased]

## 0.0.6 – 2019-01-10
### Added
- add logjob option to keep gps on between fixes
[#8](https://gitlab.com/eneiluj/phonetrack-android/issues/8) @roydenyates

### Changed
- change behaviour of logjob edit : back=cancel, menuSaveIcon=save
[#6](https://gitlab.com/eneiluj/phonetrack-android/issues/6) @Tobiasff3200
- show session selection dialog when creating phonetrack logjob
[#8](https://gitlab.com/eneiluj/phonetrack-android/issues/8) @roydenyates

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
