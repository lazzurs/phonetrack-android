#!/usr/bin/env bash
# Fails if a commit since BASE doesn't follow Conventional Commits: release notes and
# CHANGELOG.md are generated from these messages (cliff.toml).
#
#   scripts/release/check-commits.sh origin/main
set -euo pipefail

base=${1:?usage: check-commits.sh BASE_REF}
pattern='(feat|fix|perf|refactor|build|ci|chore|docs|test|style|revert)(\([a-z0-9._/-]+\))?!?: .+'
bad=$(git log --no-merges --format='%h %s' "$base..HEAD" | grep -vE "^[0-9a-f]+ $pattern" || true)
if [[ -n $bad ]]; then
    echo "::error::These commits don't follow Conventional Commits (type(scope): description):" >&2
    echo "$bad" >&2
    echo "Types: feat, fix, perf, refactor, build, ci, chore, docs, test, style, revert." >&2
    echo "Security fixes: fix(security): ... See RELEASING.md." >&2
    exit 1
fi
echo "commit messages OK ($(git rev-list --no-merges --count "$base..HEAD") commits)"
