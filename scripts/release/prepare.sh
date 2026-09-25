#!/usr/bin/env bash
# Checks that a release is consistent and writes its release notes.
#
#   scripts/release/prepare.sh [TAG] [OUT_DIR]
#
# TAG defaults to v<appVersionName> (a dry run of the next release). Checks:
#   - TAG is vX.Y.Z (full release) or vX.Y.Z-suffix (pre-release, e.g. v0.3.0-rc.1)
#   - X.Y.Z equals appVersionName in gradle.properties
#   - appVersionCode is higher than the one of the previous release tag
#   - CHANGELOG.md has a "## [X.Y.Z]" section (a pre-release may use "## [Unreleased]")
#   - a full release has fastlane/metadata/android/en-US/changelogs/<appVersionCode>.txt
# Writes OUT_DIR/RELEASE_NOTES.md (default: release-out) and prints key=value lines
# (also appended to $GITHUB_OUTPUT when set): version, tag, prerelease, version_code.
set -euo pipefail

root=$(git rev-parse --show-toplevel)
cd "$root"

fail() {
    echo "::error::$*" >&2
    exit 1
}

prop() {
    grep -E "^$1=" gradle.properties | head -1 | cut -d= -f2- | tr -d '[:space:]'
}

version_name=$(prop appVersionName)
version_code=$(prop appVersionCode)
[[ $version_name =~ ^[0-9]+\.[0-9]+\.[0-9]+$ ]] || fail "appVersionName '$version_name' in gradle.properties is not X.Y.Z"
[[ $version_code =~ ^[0-9]+$ ]] || fail "appVersionCode '$version_code' in gradle.properties is not a number"

tag=${1:-v$version_name}
out_dir=${2:-release-out}

[[ $tag =~ ^v([0-9]+\.[0-9]+\.[0-9]+)(-[0-9A-Za-z][0-9A-Za-z.-]*)?$ ]] \
    || fail "tag '$tag' must be vX.Y.Z (release) or vX.Y.Z-suffix (pre-release)"
base=${BASH_REMATCH[1]}
suffix=${BASH_REMATCH[2]}
[[ $base == "$version_name" ]] \
    || fail "tag $tag does not match appVersionName $version_name in gradle.properties"
prerelease=false
[[ -n $suffix ]] && prerelease=true

# versionCode of a tag: gradle.properties since 0.2.0, the module build file before that
code_at() {
    local ref=$1 code
    code=$(git show "$ref:gradle.properties" 2>/dev/null | grep -E '^appVersionCode=' | cut -d= -f2 | tr -d '[:space:]' || true)
    if [[ -z $code ]]; then
        code=$( { git show "$ref:app/build.gradle.kts" 2>/dev/null || git show "$ref:app/build.gradle" 2>/dev/null; } \
            | grep -oE 'versionCode[ =]+[0-9]+' | grep -oE '[0-9]+' | head -1 || true)
    fi
    echo "$code"
}

# previous release: the newest v* tag other than this one that is an ancestor of HEAD
previous=$(git tag --list 'v*' --merged HEAD --sort=-creatordate | grep -vx "$tag" | head -1 || true)
if [[ -n $previous ]]; then
    previous_code=$(code_at "$previous")
    if [[ -n $previous_code ]] && (( version_code <= previous_code )); then
        fail "appVersionCode $version_code must be higher than $previous_code (from $previous): Android refuses to install a lower or equal versionCode as an update"
    fi
fi

# release notes: the CHANGELOG section of this version, without its heading
section() {
    awk -v want="$1" '
        /^## / {
            if (found) exit
            heading = $0
            sub(/^## +/, "", heading)
            sub(/^\[/, "", heading)
            split(heading, parts, /[] ]/)
            if (parts[1] == want) { found = 1; next }
        }
        found { print }
    ' CHANGELOG.md
}
notes=$(section "$base")
if [[ -z ${notes//[[:space:]]/} ]] && [[ $prerelease == true ]]; then
    notes=$(section "Unreleased")
fi
[[ -n ${notes//[[:space:]]/} ]] \
    || fail "CHANGELOG.md has no '## [$base]' section with content: write the release notes there first"

if [[ $prerelease == false ]]; then
    [[ -s fastlane/metadata/android/en-US/changelogs/$version_code.txt ]] \
        || fail "missing fastlane/metadata/android/en-US/changelogs/$version_code.txt (store changelog, max 500 characters)"
fi

mkdir -p "$out_dir"
printf '%s\n' "$notes" | sed -e '/./,$!d' > "$out_dir/RELEASE_NOTES.md"

result="version=${tag#v}
tag=$tag
prerelease=$prerelease
version_code=$version_code"
echo "$result"
if [[ -n ${GITHUB_OUTPUT:-} ]]; then
    echo "$result" >> "$GITHUB_OUTPUT"
fi
