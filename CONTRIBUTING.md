# Contributing guide

> This repository is [lazzurs/phonetrack-android](https://github.com/lazzurs/phonetrack-android), a fork of
> [eneiluj/phonetrack-android](https://gitlab.com/eneiluj/phonetrack-android). This fork has its own application ID
> and behaviour, so please report bugs and feature requests for this app in
> [this fork's issue tracker](https://github.com/lazzurs/phonetrack-android/issues). Problems that also exist in the
> original app may be worth mentioning [upstream](https://gitlab.com/eneiluj/phonetrack-android/issues) too.

## Submitting bug reports

If you find a bug, feel free to [open an issue](https://github.com/lazzurs/phonetrack-android/issues). But please provide these information in the comment:

### Copy & Paste
    **Android version:** e. g. 14 or 16

    **Device**: e. g. LG G4

    **System language**: English (US), German, ...

    **App version:** e. g. v0.0.6

    **App source:** self-built, GitHub release, ...

    **Steps to reproduce:**
      1. open the app
      2. click on a log job
      3. use the top left back-arrow
      4. ...

## Commit messages (this fork)

Release notes and `CHANGELOG.md` are generated from commit messages, so they follow
[Conventional Commits](https://www.conventionalcommits.org), e.g. `fix: keep the alarm volume`
or `fix(security): only accept SMS from allowed senders`. CI checks it; the types and the release
process are in [RELEASING.md](RELEASING.md).

Please don't change the app version (`appVersionName` / `appVersionCode` in `gradle.properties`) in a
pull request: the release workflow sets it when your change is released, and CI rejects version
changes from forks.

## Adding new features

If you want to contribute on the code, you can fork the project, commit your changes and make a pull request. For bigger features/changes, it's better to first [open an issue](https://github.com/lazzurs/phonetrack-android/issues) and ask if this feature is needed or wanted – it would be very disappointing if you add a new cool feature and your pull request will be rejected by some reasons you didn't thought about.
