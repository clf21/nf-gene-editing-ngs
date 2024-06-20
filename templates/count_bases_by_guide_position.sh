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
  failed >error.yaml
fi
