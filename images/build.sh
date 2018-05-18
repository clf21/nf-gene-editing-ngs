#!/usr/bin/env bash

# Base directory containing this script and its current commit ID
declare BASE_DIR="$(dirname "$(readlink -f "${BASH_SOURCE[0]}")")"
declare BASE_COMMIT_ID="$(git log --pretty=tformat:"%h" -n1 "${BASE_DIR}")"

# Ensure Docker or Podman commands are available
if ! command -v docker >/dev/null; then
  if command -v podman >/dev/null; then
    shopt -s expand_aliases
    alias docker=podman
  else
    >&2 echo "ERROR: Neither docker or podman commands are available. Cannot build image."
    exit 1
  fi
fi

# Build image
docker build \
  -t "crispr:${BASE_COMMIT_ID}" \
  -t "crispr:latest" \
  -f "${BASE_DIR}/Dockerfile" \
  "${BASE_DIR}"
