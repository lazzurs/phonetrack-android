#!/usr/bin/env bash
# Fails if a workflow uses an action that is not pinned to a full commit SHA, or has no top-level
# permissions: block. A tag such as v4 can be moved to other code by whoever controls the action's
# repository; a commit SHA can't. Dependabot (.github/dependabot.yml) proposes the updates.
#
#   scripts/release/check-workflows.sh [DIR]     DIR defaults to .github/workflows
set -euo pipefail

dir=${1:-$(git rev-parse --show-toplevel)/.github/workflows}
status=0
shopt -s nullglob
files=("$dir"/*.yml "$dir"/*.yaml)
(( ${#files[@]} > 0 )) || { echo "::error::no workflows in $dir" >&2; exit 1; }

for file in "${files[@]}"; do
    # every "uses:" except local actions and workflows (./...) must be owner/repo[/path]@<40 hex> # vX.Y.Z
    bad=$(grep -nE '^[[:space:]]*(-[[:space:]]+)?uses:' "$file" \
        | grep -vE 'uses:[[:space:]]+\./' \
        | grep -vE 'uses:[[:space:]]+[A-Za-z0-9._/-]+@[0-9a-f]{40}[[:space:]]+#[[:space:]]*v[0-9]+(\.[0-9]+)*[[:space:]]*$' || true)
    if [[ -n $bad ]]; then
        echo "::error::$file: pin each action to a full commit SHA followed by '# vX.Y.Z':" >&2
        echo "$bad" >&2
        status=1
    fi
    if ! grep -qE '^permissions:' "$file"; then
        echo "::error::$file: no top-level 'permissions:' block (start from 'contents: read')" >&2
        status=1
    fi
done
(( status == 0 )) && echo "workflows OK (${#files[@]} files: actions pinned to commit SHAs, permissions set)"
exit "$status"
