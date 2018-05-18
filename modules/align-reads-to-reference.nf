// Nextflow wrapper to align_reads_to_reference.pl
process _alignReadsToReference {
    label "usesSamtools"
    label "usesBowtie2"

    errorStrategy "ignore"

    tag "${meta.id}"

    input:
        // Bowtie2 Index Path Prefix
        // i.e., path containing `$BASENAME.*.bt2` files, where BASENAME is
        // taken from `pattern` in YAML config
        path bt2IndexPath

        // Input Read
        tuple val(meta), path(sampleFastq)

    output:
        // Output from Samtools (BAM and its index)
        tuple val(meta), path("aligned.bam*"), emit: output

        // Additional details about read alignment
        tuple val(meta), path("info.yaml"), emit: info

    shell:
        assert meta.hasKeys(Metadata.Keys.SampleId)

        // Template tags:
        // * bt2IndexPath            Bowtie2 reference/index path
        // * sampleFastq             Sample FASTQ filename
        // * meta.sampleName         Sample name
        // * params.bowtie2.pattern  Reference file pattern/prefix
        // * task.ext.bowtie2        Bowtie2 command definition
        // * task.ext.samtools       Samtools command definition
        template "align_reads_to_reference.pl"
}

workflow alignReadsToReference {
    take:
        // Prepared Reads Channel
        // Tuples of (Metadata<Sample name, Read ID>, FASTQ Path)
        preparedReads

    main:
        _alignReadsToReference(
            file(params.bowtie2.dir),
            preparedReads
        )
        | set { passedAlignments }

        // To determine failures, we only care about the input
        // identifiers; so drop everything else
        preparedReads
        | map { meta, _sample -> meta }
        | set { inputIdentifiers }

        // NOTE If _no_ alignments succeeded, then the result of the
        // join will just be inputIdentifiers; hence the filter
        // condition. That is, if the input is a Metadata object, then
        // all alignments must have failed, otherwise we search for
        // those that matched in the outer join.
        inputIdentifiers
        | join(passedAlignments.output, remainder: true)
        | filter { (it instanceof Metadata) || (it[1] == null) }
        | map { it instanceof Metadata ? it : it[0] }
        | set { failedAlignments }

    emit:
        // Channel of tuples, of the form:
        // * Metadata<Sample name, Read ID>
        // * Aligned reads (BAM file and its index)
        output = passedAlignments.output

        // Channel of tuples, of the form:
        // * Metadata<Sample name, Read ID>
        // * Alignment info
        info = passedAlignments.info

        // Channel of failed Metadata<Sample name, Read ID>
        failed = failedAlignments
}
