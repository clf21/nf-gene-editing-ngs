#!/usr/bin/env bash

set -eu

# Base directory containing this script and its current commit ID
declare BASE_DIR="$(dirname "$(readlink -f "${BASH_SOURCE[0]}")")"
declare BASE_COMMIT_ID="$(git log --pretty=tformat:"%h" -n1 "${BASE_DIR}")"

# Get image name from environment, or set a default
declare IMAGE_NAME_DEFAULT="nf-gene-editing-ngs"
declare IMAGE_NAME="${IMAGE_NAME-${IMAGE_NAME_DEFAULT}}"

# Output file
declare OUTPUT_FILE="${IMAGE_NAME}_${BASE_COMMIT_ID}.sif"

# Check if singularity is available
if ! command -v singularity >/dev/null && ! command -v apptainer >/dev/null; then
  >&2 echo "ERROR: Neither singularity nor apptainer commands are available. Cannot build image."
  exit 1
fi

# Use apptainer if available (newer name for singularity), otherwise singularity
declare SINGULARITY_CMD="singularity"
if command -v apptainer >/dev/null; then
  SINGULARITY_CMD="apptainer"
fi

echo "Building Singularity image from definition file..."
echo "Output: ${OUTPUT_FILE}"
echo ""
echo "Note: This requires sudo or --fakeroot for building"
echo ""

# Build Singularity image from definition file
# Use --fakeroot if running without sudo (requires user namespaces)
${SINGULARITY_CMD} build \
  --force \
  "${OUTPUT_FILE}" \
  "${BASE_DIR}/Singularity.def"

echo ""
echo "Build complete: ${OUTPUT_FILE}"
echo ""
echo "To use this image with Nextflow, set:"
echo "  export GENA_IMAGE=\"${PWD}/${OUTPUT_FILE}\""
echo ""
echo "Or update your Nextflow config:"
echo "  singularity.enabled = true"
echo "  process.container = '${PWD}/${OUTPUT_FILE}'"
