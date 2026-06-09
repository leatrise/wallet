#!/usr/bin/env bash
set -euo pipefail

repo_root="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
variant="${REPRO_VARIANT:-universal}"
work_dir="${GEM_REPRO_WORK_DIR:-${RUNNER_TEMP:-$repo_root/android/build/reproducible}/gem-repro}"

verify_args=(
  android/reproducible/verify.sh
  --source .
  --variant "$variant"
  --work-dir "$work_dir"
  --keep
)

printf -v verify_command '%q ' "${verify_args[@]}"
cd "$repo_root"
nix-shell shell.nix --run "$verify_command"
