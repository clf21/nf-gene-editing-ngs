#!/usr/bin/env bash

set -eu

# Base directory containing this script and its current commit ID
declare BASE_DIR="$(dirname "$(readlink -f "${BASH_SOURCE[0]}")")"
declare BASE_COMMIT_ID="$(git log --pretty=tformat:"%h" -n1 "${BASE_DIR}")"

# Get image name from environment, or set a default
declare IMAGE_NAME_DEFAULT="nf-gene-editing-ngs"
declare IMAGE_NAME="${IMAGE_NAME-${IMAGE_NAME_DEFAULT}}"

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
  -t "${IMAGE_NAME}:${BASE_COMMIT_ID}" \
  -t "${IMAGE_NAME}:latest" \
  -f "${BASE_DIR}/Dockerfile" \
  "${BASE_DIR}"
