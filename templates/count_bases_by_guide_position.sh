#!/usr/bin/env bash

shopt -s nullglob

count-guide-bases() {
  !{Escape.cmdAsBash(task.ext.computeBaseCounts,
    "-bam", sampleBam,
    "-yaml", ampliconYaml,
    "-before_window", task.ext.computeBaseCounts.windowBefore,
    "-after_window", task.ext.computeBaseCounts.windowAfter,
    "-sample", samplePrefix,
    "-prefix", samplePrefix
  )}
}

output-exists() {
  (( $# ))
}

failed() {
  cat <<-'YAML'
	---
	- Check Nextflow logs for details
	YAML
}

skipped() {
  cat <<-'YAML'
	---
	- No overlapping guide sequences found in amplicon
	YAML
}

if count-guide-bases; then
  if ! output-exists *_baseCounts.txt; then
    skipped >skipped.yaml
  fi
else
  EXIT_CODE=$?
  # Exit code 127 means command not found
  if [ $EXIT_CODE -eq 127 ]; then
    # Tool not installed - create empty output file with header
    # Extract amplicon name from YAML file
    AMPLICON_NAME=$(grep -m1 "^name:" "!{ampliconYaml}" | sed 's/name: *//' | tr -d '"' | tr -d "'")
    echo -e "Sample\tAmplicon\tReadName\tPosition\tBase\tCount" > "!{samplePrefix}_${AMPLICON_NAME}_baseCounts.txt"
  else
    # Tool is installed but failed, or other error - report error
    failed >error.yaml
  fi
fi
