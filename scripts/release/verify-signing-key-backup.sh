#!/usr/bin/env bash
# Checks a backup of the release signing key made by .github/workflows/backup-signing-key.yml:
# decrypts both files with your age identity, and checks that the keystore opens with the
# password, holds the private key under the alias phonetrack-fork, and that the key's certificate
# is the release certificate pinned in scripts/release/release-cert.sha256.
#
#   scripts/release/verify-signing-key-backup.sh IDENTITY signing-key-<stamp>.age signing-key-password-<stamp>.age
#
# IDENTITY is your age identity file (the private key matching the recipient the workflow encrypted
# to; plugin identities such as age-plugin-yubikey work too). Run it on your own machine.
#
# Prints only the SHA-256 of the two encrypted files (compare them with the workflow run's
# receipts) and of the certificate. Never prints the key or the password: the password stays in
# this script's environment, the keystore is decrypted into a private temporary directory that is
# deleted on exit, and the encrypted .age files are what you keep as the backup.
# Needs age, keytool (any JDK) and sha256sum or shasum.
set -euo pipefail

usage="usage: verify-signing-key-backup.sh IDENTITY KEYSTORE.age PASSWORD.age"
(( $# == 3 )) || { echo "$usage" >&2; exit 2; }
identity=$1 keystore_age=$2 password_age=$3
alias_name=phonetrack-fork

fail() {
    echo "FAIL: $*" >&2
    exit 1
}

for tool in age keytool; do
    command -v "$tool" >/dev/null || fail "$tool is not installed"
done
for file in "$identity" "$keystore_age" "$password_age"; do
    [[ -f $file ]] || fail "$file: no such file"
done

sha256() {
    if command -v sha256sum >/dev/null; then
        sha256sum | cut -d' ' -f1
    else
        shasum -a 256 | cut -d' ' -f1
    fi
}

pin_file=$(dirname "${BASH_SOURCE[0]}")/release-cert.sha256
pinned=$(grep -vE '^[[:space:]]*(#|$)' "$pin_file" | tr -d '[:space:]:' | tr '[:upper:]' '[:lower:]')
[[ $pinned =~ ^[0-9a-f]{64}$ ]] || fail "$pin_file must contain exactly one SHA-256 digest"

echo "encrypted keystore: sha256 $(sha256 < "$keystore_age")  $keystore_age"
echo "encrypted password: sha256 $(sha256 < "$password_age")  $password_age"

umask 077
work=$(mktemp -d "${TMPDIR:-/tmp}/signing-key-check.XXXXXXXX")
trap 'rm -rf -- "$work"' EXIT
keystore=$work/release.keystore

age -d -i "$identity" -o "$keystore" -- "$keystore_age" || fail "could not decrypt $keystore_age"
# the password only ever lives in this variable, passed to keytool through the environment
SIGNING_KEY_BACKUP_PASSWORD=$(age -d -i "$identity" -- "$password_age") \
    || fail "could not decrypt $password_age"
[[ -n $SIGNING_KEY_BACKUP_PASSWORD ]] || fail "the decrypted password is empty"
export SIGNING_KEY_BACKUP_PASSWORD

keytool_store() {
    keytool "$@" -keystore "$keystore" -storepass:env SIGNING_KEY_BACKUP_PASSWORD
}

entry=$(keytool_store -list -alias "$alias_name" 2>&1) \
    || fail "the keystore does not open with the password, or has no alias $alias_name"
[[ $entry == *PrivateKeyEntry* ]] || fail "alias $alias_name is not a private key entry"

# a certificate signing request needs the private key itself, so this proves the key (not only
# its certificate) is there and opens with the password; the request is discarded
keytool_store -certreq -alias "$alias_name" -keypass:env SIGNING_KEY_BACKUP_PASSWORD >/dev/null 2>&1 \
    || fail "the private key of $alias_name does not open with the password"

digest=$(keytool_store -exportcert -alias "$alias_name" 2>/dev/null | sha256) \
    || fail "could not export the certificate of $alias_name"
echo "certificate:        sha256 $digest"
echo "pinned:             sha256 $pinned"
[[ $digest == "$pinned" ]] || fail "the certificate is NOT the pinned release certificate"
echo "OK: the backup holds the release key ($alias_name), it opens with the backed-up password," \
    "and its certificate matches scripts/release/release-cert.sha256"
