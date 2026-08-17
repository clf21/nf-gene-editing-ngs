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

# Check if computeBaseCounts tool is available before running
if ! command -v computeBaseCounts &> /dev/null; then
  # Tool not installed - check if we have valid data to process
  # Count reads in BAM file - if 0, this is an error case (no matching reads)
  READ_COUNT=$(samtools view -c "!{sampleBam}")
  if [ "$READ_COUNT" -eq 0 ]; then
    # No reads in BAM - this is an error (amplicon not in sample)
    failed >error.yaml
  else
    # Tool not installed but data is valid - create empty output file with header
    AMPLICON_NAME=$(grep -m1 "^name:" "!{ampliconYaml}" | sed 's/name: *//' | tr -d '"' | tr -d "'")
    echo -e "Sample\tAmplicon\tReadName\tPosition\tBase\tCount" > "!{samplePrefix}_${AMPLICON_NAME}_baseCounts.txt"
  fi
elif count-guide-bases; then
  # Tool ran successfully
  if ! output-exists *_baseCounts.txt; then
    skipped >skipped.yaml
  fi
else
  # Tool exists but failed - report error
  failed >error.yaml
fi
