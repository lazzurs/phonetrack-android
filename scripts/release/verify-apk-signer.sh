#!/usr/bin/env bash
# Checks which key signed APKs, against the release certificate pinned in
# scripts/release/release-cert.sha256 (the SHA-256 digest of the fork's signing certificate).
#
#   scripts/release/verify-apk-signer.sh release APK...   every APK must be signed with the release
#                                                         key, and with nothing else
#   scripts/release/verify-apk-signer.sh other APK...     no APK may be signed with the release key
#                                                         (dry runs away from main, pull requests)
#
# The release workflow runs it after assembling and again before publishing, so a release that is
# debug-signed or signed with another key fails instead of being published.
# Needs apksigner: on PATH, or in the newest build-tools of $ANDROID_HOME / $ANDROID_SDK_ROOT.
set -euo pipefail

usage="usage: verify-apk-signer.sh release|other APK..."
expect=${1:?$usage}
shift
[[ $expect == release || $expect == other ]] || { echo "$usage" >&2; exit 2; }
(( $# > 0 )) || { echo "$usage" >&2; exit 2; }

fail() {
    echo "::error::$*" >&2
    exit 1
}

pin_file=$(dirname "${BASH_SOURCE[0]}")/release-cert.sha256
pinned=$(grep -vE '^[[:space:]]*(#|$)' "$pin_file" | tr -d '[:space:]:' | tr '[:upper:]' '[:lower:]')
[[ $pinned =~ ^[0-9a-f]{64}$ ]] || fail "$pin_file must contain exactly one SHA-256 digest (64 hex digits)"

apksigner=$(command -v apksigner || true)
if [[ -z $apksigner ]]; then
    sdk=${ANDROID_HOME:-${ANDROID_SDK_ROOT:-}}
    [[ -n $sdk ]] || fail "apksigner is not on PATH and ANDROID_HOME is not set"
    apksigner=$(find "$sdk/build-tools" -mindepth 2 -maxdepth 2 -name apksigner 2>/dev/null | sort -V | tail -1)
    [[ -n $apksigner ]] || fail "no apksigner in $sdk/build-tools"
fi

for apk in "$@"; do
    [[ -f $apk ]] || fail "$apk: no such file"
    # fails if the APK is unsigned or its signature is invalid
    certs=$("$apksigner" verify --print-certs "$apk") || fail "$apk: apksigner could not verify the signature"
    # one line per signer (and per signing scheme or rotated key, if any)
    digests=$(grep -E 'certificate SHA-256 digest: ' <<< "$certs" | sed -E 's/.*digest: *//' \
        | tr -d '[:blank:]:' | tr '[:upper:]' '[:lower:]' | sort -u)
    [[ -n $digests ]] || fail "$apk: apksigner printed no certificate digest"
    if [[ $expect == release ]]; then
        [[ $digests == "$pinned" ]] \
            || fail "$apk is not signed with the release key (signer certificate SHA-256: ${digests//$'\n'/, }; expected $pinned)"
        echo "$apk: signed with the release key ($pinned)"
    else
        ! grep -qxF "$pinned" <<< "$digests" \
            || fail "$apk is signed with the release key, which must not happen in this run"
        echo "$apk: not signed with the release key (signer certificate SHA-256: ${digests//$'\n'/, })"
    fi
done
