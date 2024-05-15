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
        // NOTE In the reduction step, we don't care about the returned
        // values; we know the process succeeded by virtue of reaching
        // this step. As such, this is just used as a synchronisation
        // mechanism for when all references are downloaded.
        Channel.fromList(params._ref.collect { refId, refInfo -> [
            refId,
            refInfo.bowtie2.dir,
            refInfo.bowtie2.prefix,
            refInfo.bowtie2._url
        ]})
        | _fetchRef
        | reduce { _a, _b -> true }
        | set { isDeployed }

    emit:
        isDeployed
}

// ./run deploy.nf [--genome_dir <reference genome base directory>] [OPTIONS...]
workflow {
    deploy()
}
