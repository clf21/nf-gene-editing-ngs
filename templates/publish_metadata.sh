#!/usr/bin/env bash

# Publish serialised parameters to params.yaml
cat <<'YAML' >params.yaml
!{paramsYaml}
YAML

# Prepare published amplicons
cp "!{preparedAmplicons}" "amplicons.yaml"
