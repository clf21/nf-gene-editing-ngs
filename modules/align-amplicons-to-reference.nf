// Nextflow wrapper to align_amplicons_to_reference.pl
process _alignAmpliconsToReference {
    label "process_low"
    label "usesBowtie2"

    input:
        // Bowtie2 Index Path
        // i.e., path containing `$BASENAME.*.bt2` files, where BASENAME is
        // taken from `prefix` in YAML config
        path bt2IndexPath

        // Amplicons YAML Path
        path ampliconsYaml

    output:
        // Associates amplicon name with alignment
        path "alignments.yaml"

    shell:
        // Template tags:
        // * ampliconsYaml              Amplicons YAML file
        // * bt2IndexPath               Bowtie2 reference/index path
        // * params.bowtie2.prefix      Reference file prefix
        // * params.genome              Reference genome ID
        // * task.ext.bowtie2_amplicon  Bowtie2 command definition for amplicon alignment
        template "align_amplicons_to_reference.pl"
}

workflow alignAmpliconsToReference {
    take:
        // Amplicons YAML Path Channel
        ampliconsYaml

    main:
        _alignAmpliconsToReference(
            params.bowtie2.dir,
            ampliconsYaml
        )
        | set { aligned }

    emit:
        aligned
}
