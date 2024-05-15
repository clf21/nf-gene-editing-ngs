// Nextflow wrapper to merge_paired_reads.pl
process _mergePairedReads {
    label "usesFLASH"

    tag "${meta.id}"

    input:
        // Sample Read (Metadata<Sample name> and FASTQ pair)
        tuple val(meta),
              path(sampleFastqPair, name: "*.fastq.gz")

    output:
        // FLASH output
        tuple val(meta),
              path("out.*.fastq.gz"),
              emit: output

        // Additional details about read merging
        tuple val(meta),
              path("info.yaml"),
              emit: info

    shell:
        assert sampleFastqPair.size() == 2

        // Template tags:
        // * sampleFastqPair            Array of sample FASTQ reads (read 1 and read 2, respectively)
        // * task.ext.flash             FLASH command definition
        // * taks.ext.flash.minOverlap  FLASH's --min-overlap argument
        // * taks.ext.flash.maxOverlap  FLASH's --max-overlap argument
        template "merge_paired_reads.pl"
}

workflow mergePairedReads {
    take:
        // Paired Read Channel
        // Tuples of (Metadata<Sample name>, [FASTQ_1 Path, FASTQ_2 Path])
        samplePairFastq

    main:
        if (params.merge_mode == MergeMode.NoMerge) {
            // Bypass the FLASH step when no merging is required...
            samplePairFastq
            | map { meta, _samples -> [ meta, [] ] }
            | set { flashMerged }

            merged = flashMerged
            info = Channel.empty()

        } else {
            // ...Otherwise, run the FLASH merging step
            _mergePairedReads(samplePairFastq)
            | set { flashMerged }

            merged = flashMerged.output
            info = flashMerged.info
        }

        samplePairFastq
        | join(merged)
        | flatMap { meta, originals, flashOutput ->
            // For some unknown reason, params.merge_mode is being
            // serialised to a string in this closure, so we have to
            // convert it back into a MergeMode enum (??)
            switch(MergeMode.from(params.merge_mode)) {
                case MergeMode.Auto:
                    merged = flashOutput[0]  // FLASH's out.extendedFrags.fastq.gz
                    read1  = flashOutput[1]  // FLASH's out.notCombined_1.fastq.gz
                    read2  = flashOutput[2]  // FLASH's out.notCombined_2.fastq.gz
                    break

                case MergeMode.Merge:
                    merged = flashOutput[0]
                    read1  = null            // null entries dropped later...
                    read2  = null
                    break

                case MergeMode.Both:
                    merged = flashOutput[0]
                    read1  = originals[0]    // Original input read 1
                    read2  = originals[1]    // Original input read 2
                    break

                case MergeMode.NoMerge:
                    merged = null
                    read1  = originals[0]
                    read2  = originals[1]
                    break
            }

            [
                [ meta << "Merged", merged ],
                [ meta << "Read1",  read1 ],
                [ meta << "Read2",  read2 ]
            ]
        }
        | filter { _meta, sample -> sample != null }
        | set { output }

    emit:
        // Channel of tuples, of the form:
        // * Metadata<Sample name, Read ID>
        // * Merged reads
        output = output

        // Channel of tuples, of the form:
        // * Metadata<Sample name>
        // * Merging info
        info = info
}
