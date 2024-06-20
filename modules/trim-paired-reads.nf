// Nextflow wrapper to trim_paired_reads.pl
process _trimPairedReads {
    label "usesTrimmomatic"

    tag "${meta.id()}"

    input:
        // Sample Read (Metadata<Sample name> and FASTQ pair)
        // NOTE The input downsampled.fastq.gz files are renamed by
        // Nextflow to downsampled-{1,2}.fastq.gz, automatically
        tuple val(meta),
              path(sampleFastqPair, name: "downsampled-*.fastq.gz")

    output:
        // Trimmed read pair
        tuple val(meta),
              path("read*_trimmed.fastq.gz"),
              emit: output

        // Additional details about read trimming
        tuple val(meta),
              path("info.yaml"),
              emit: info

    shell:
        assert sampleFastqPair.size() == 2

        // Template tags:
        // * sampleFastqPair                   Array of sample FASTQ reads (read 1 and read 2, respectively)
        // * task.ext.trimmomatic              Trimmomatic command definition
        // * task.ext.trimmomatic.adapterPath  Path to adapter trimming FASTA file (optional)
        // * task.ext.trimmomatic.headcrop     Headcrop (optional)
        template "trim_paired_reads.pl"
}

workflow trimPairedReads {
    take:
        // Paired Read Channel
        // Tuples of (Metadata<Sample name>, [FASTQ_1 Path, FASTQ_2 Path])
        sampleFastqPair

    main:
        if (params._do_trimming) {
            _trimPairedReads(sampleFastqPair)
            | set { trimmed }

            reads = trimmed.output
            info = trimmed.info

        } else {
            // Just pass-through, when trimming is skipped
            reads = sampleFastqPair
            info = Channel.empty()
        }

    emit:
        // Channel of tuples, of the form:
        // * Metadata<Sample name>
        // * Trimmed read pair (read 1 and 2, respectively)
        output = reads

        // Channel of tuples, of the form:
        // * Metadata<Sample name>
        // * Trimming info
        info = info
}
