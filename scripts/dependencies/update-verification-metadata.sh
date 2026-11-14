#!/usr/bin/env bash
# Regenerates gradle/verification-metadata.xml: the SHA-256 checksum of every file Gradle downloads
# for the build (plugins, libraries, their .pom/.module metadata, lint, aapt2). Gradle refuses
# anything whose checksum is not in that file, so run this after changing a dependency, the Android
# Gradle plugin or a settings plugin, then REVIEW THE DIFF before committing it: the checksums
# written are those of whatever was downloaded just now (RELEASING.md, "Dependencies").
#
#   scripts/dependencies/update-verification-metadata.sh [GRADLE ARGUMENTS]
#
# e.g. behind a proxy: ... -Dhttps.proxyHost=localhost -Dhttps.proxyPort=3128
#
# The <configuration> block (and the comment above it) is kept as it is; all <components> are
# dropped and written again, so entries of versions that are no longer used disappear. Runs every
# task that CI, the release workflow and developers run, since Gradle only records what a build
# actually resolves; takes a few minutes. Needs the Android SDK (ANDROID_HOME).
set -euo pipefail

cd "$(git rev-parse --show-toplevel)"
metadata=gradle/verification-metadata.xml

# Every variant of everything: build.yml and release.yml run a subset of these (lintDevDebug,
# test<Flavor>DebugUnitTest, assembleDevDebug, assembleNormalRelease, assemblePlayRelease).
# Keep this list a superset of the Gradle tasks in .github/workflows.
tasks=(
    :app:assemble
    :app:assembleAndroidTest
    :app:test
    :app:lintDevDebug :app:lintNormalDebug :app:lintPlayDebug
)

# No configuration or build cache and no up-to-date tasks: a task that doesn't run may not resolve
# what it would download (lint and aapt2 are only resolved when their tasks execute).
# --refresh-dependencies: with dependencies already in the Gradle cache, Gradle leaves out some
# parent POMs and BOMs that a build with an empty cache (CI, the release) then asks for.
gradle=(./gradlew --console=plain --no-configuration-cache --no-build-cache --refresh-dependencies
    --write-verification-metadata sha256 "$@")

grep -q '<components>' "$metadata" || { echo "error: no <components> in $metadata" >&2; exit 1; }
backup=$(mktemp)
cp "$metadata" "$backup"
restore() {
    status=$?
    if (( status != 0 )); then
        cp "$backup" "$metadata"
        echo "error: failed, $metadata is unchanged" >&2
    fi
    rm -f "$backup"
    exit "$status"
}
trap restore EXIT

# keep everything above <components>, start again with no components
{
    sed '/<components>/,$d' "$backup"
    printf '   <components>\n   </components>\n</verification-metadata>\n'
} > "$metadata"

"${gradle[@]}" --rerun-tasks "${tasks[@]}"

# aapt2 is the one artifact that differs per operating system: add the other two
aapt2_version=$(sed -n 's/.*<component group="com[.]android[.]tools[.]build" name="aapt2" version="\([^"]*\)".*/\1/p' "$metadata")
[[ -n $aapt2_version && $aapt2_version != *$'\n'* ]] \
    || { echo "error: expected exactly one aapt2 version in $metadata, found: '${aapt2_version}'" >&2; exit 1; }
"${gradle[@]}" --init-script scripts/dependencies/other-platforms.init.gradle \
    "-Paapt2Version=$aapt2_version" resolveAapt2ForAllPlatforms

# A published version never changes: a different checksum for a file that was already recorded
# means the repository now serves other bytes (JitPack can rebuild an artifact; anything else would
# be an incident). Refuse, and leave the old file in place.
checksums() {  # one line per file: group:name:version/file sha256
    awk -F'"' '
        /<component /  { component = $2 ":" $4 ":" $6 }
        /<artifact /   { artifact = $2 }
        /<sha256 /     { print component "/" artifact, $2 }' "$1" | sort
}
changed=$(join <(checksums "$backup") <(checksums "$metadata") | awk '$2 != $3 { print "  " $1 ": was " $2 ", now " $3 }')
if [[ -n $changed ]]; then
    echo "error: the checksum of a file that was already recorded has changed, for the same version:" >&2
    echo "$changed" >&2
    echo "Find out why before accepting it (to accept: remove that <component> from $metadata, commit, run this again)." >&2
    exit 1
fi

# Gradle drops the comment at the top of the file when it writes it: put it back
if ! grep -q '<!--' "$metadata"; then
    comment=$(mktemp)
    sed -n '/<!--/,/-->/p' "$backup" > "$comment"
    awk -v comment="$comment" '
        /<configuration>/ { while ((getline line < comment) > 0) print line }
        { print }' "$metadata" > "$metadata.tmp"
    mv "$metadata.tmp" "$metadata"
    rm -f "$comment"
fi

echo
echo "$metadata: $(grep -c '<component ' "$metadata") components, $(grep -c '<sha256 ' "$metadata") checksums"
git --no-pager diff --stat -- "$metadata"
echo "Review the diff (only the dependencies you changed, and what they pull in, should differ), then commit it."
