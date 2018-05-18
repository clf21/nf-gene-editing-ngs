// Nextflow wrapper to trim_single_reads.pl
process _trimSingleReads {
    label "usesTrimmomatic"

    tag "${meta.id}"

    input:
        // Sample Read (Metadata<Sample name> and FASTQ pair)
        tuple val(meta), path(sampleFastq, name: "downsampled.fastq.gz")

    output:
        // Trimmed reads
        tuple val(meta), path("read_trimmed.fastq.gz"), emit: output

        // Additional details about read trimming
        tuple val(meta), path("info.yaml"), emit: info

    shell:
        // Template tags:
        // * sampleFastq                       Sample FASTQ reads
        // * task.ext.trimmomatic              Trimmomatic command definition
        // * task.ext.trimmomatic.adapterPath  Path to adapter trimming FASTA file (optional)
        // * task.ext.trimmomatic.headcrop     Headcrop (optional)
        template "trim_single_reads.pl"
}

workflow trimSingleReads {
    take:
        // Single Read Channel
        // Tuples of (Metadata<Sample name>, FASTQ Path)
        sampleFastq

    main:
        if (params._doTrimming) {
            _trimSingleReads(sampleFastq)
            | set { trimmed }

            reads = trimmed.output
            info = trimmed.info

        } else {
            // Just pass-through, when trimming is skipped
            reads = sampleFastq
            info = Channel.empty()
        }

        // Augment the metadata with a read ID of "Read1"
        reads
        | map { meta, trimmed -> [ meta << "Read1", trimmed ] }
        | set { readsWithReadId }

    emit:
        // Channel of tuples, of the form:
        // * Metadata<Sample name, Read ID>
        // * Trimmed read
        output = readsWithReadId

        // Channel of tuples, of the form:
        // * Metadata<Sample name>
        // * Trimming info
        info = info
}
