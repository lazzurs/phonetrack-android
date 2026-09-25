# Releasing

Releases are built and published by [`.github/workflows/release.yml`](.github/workflows/release.yml)
when a version tag is pushed. The release gets:

- **notes** from the version's section of [`CHANGELOG.md`](CHANGELOG.md), followed by GitHub's list
  of merged pull requests (grouped by label, see [`.github/release.yml`](.github/release.yml));
- the **signed APKs** `phonetrack-<version>-normal.apk` and `phonetrack-<version>-play.apk`;
- the **R8 mapping files** `phonetrack-<version>-<flavor>-mapping.txt`, to decode obfuscated stack
  traces with `retrace`;
- **`SHA256SUMS.txt`** for all of the above.

## Versions and tags

- Plain [SemVer](https://semver.org) tags: `vX.Y.Z` is a full release (marked *latest*),
  `vX.Y.Z-suffix` (e.g. `v0.3.0-rc.1`) is a pre-release.
- The version lives in one place, [`gradle.properties`](gradle.properties):
  - `appVersionName` — must equal the tag without the `v`;
  - `appVersionCode` — must be **higher than the previous release's**. Android refuses to install
    a lower or equal version code as an update.

## Steps

1. **Pick the version** and set `appVersionName` / `appVersionCode` in `gradle.properties`.
2. **Release notes**: in `CHANGELOG.md`, turn `## [Unreleased]` into `## [X.Y.Z] – YYYY-MM-DD`
   (keep an empty `## [Unreleased]` above it). Write for users: what changed for them, and anything
   they have to do after updating.
3. **Store changelog**: `fastlane/metadata/android/en-US/changelogs/<appVersionCode>.txt`,
   at most 500 characters.
4. Open a pull request. CI runs `scripts/release/prepare.sh`, which checks all of the above.
5. **Test a release build on a device** (it's optimized by R8, unlike debug builds): run the
   workflow manually (*Actions → Release → Run workflow* on `main`) for a dry run and install the
   APK from the uploaded artifact.
6. Merge, then tag the merge commit and push the tag:

   ```sh
   git switch main && git pull
   git tag -a vX.Y.Z -m "PhoneTrack X.Y.Z"
   git push origin vX.Y.Z
   ```

7. Check the published release: notes, APKs, checksums.

To check a version locally: `scripts/release/prepare.sh vX.Y.Z` (writes `release-out/RELEASE_NOTES.md`).

## Signing

The release APKs are signed with the fork's key, kept only in the repository secrets
`RELEASE_KEYSTORE_BASE64` / `RELEASE_KEYSTORE_PASSWORD` (see the workflow). Every release must be
signed with that same key, or installed apps can't be updated. Without the secrets (e.g. in a
fork of this repository) the APKs are debug-signed and the workflow warns about it.

The original PhoneTrack builds (F-Droid, GitLab) use the same application ID but a different key,
so they can't be updated in place by these releases: users uninstall them first. Say so in the
notes of a release that could reach such users (0.2.0 does).
