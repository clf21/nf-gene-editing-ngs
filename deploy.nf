// Download and unzip Bowtie2 reference index
process _fetchRef {
    tag "${referenceId}"
    storeDir "${referenceDir}"

    input:
        tuple val(referenceId),
              val(referenceDir),
              val(referencePrefix),
              val(referenceUrl)

    output:
        // All bowtie2 references must contain these six files with a common base name
        tuple path("${referencePrefix}.1.bt2"),
              path("${referencePrefix}.2.bt2"),
              path("${referencePrefix}.3.bt2"),
              path("${referencePrefix}.4.bt2"),
              path("${referencePrefix}.rev.1.bt2"),
              path("${referencePrefix}.rev.2.bt2")

    script:
        """
        wget -O- "$referenceUrl" | unzip -j -
        """
}

workflow deploy {
    main:
        // Only fetch the genome specified by params.genome
        // Check if the genome is defined in the reference configuration
        if (!params._ref.containsKey(params.genome)) {
            error "Genome '${params.genome}' is not defined in reference configuration. Available genomes: ${params._ref.keySet().join(', ')}"
        }

        Channel.fromList([[
            params.genome,
            params._ref[params.genome].bowtie2.dir,
            params._ref[params.genome].bowtie2.prefix,
            params._ref[params.genome].bowtie2._url
        ]])
        | _fetchRef
        | map { true }
        | set { isDeployed }

    emit:
        isDeployed
}

// ./run deploy.nf [--genome_dir <reference genome base directory>] [OPTIONS...]
workflow {
    deploy()
}
