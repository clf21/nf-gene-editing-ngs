Utils.preFlight(params, false)

// Download and unzip Bowtie2 reference index
process _fetchRef {
    tag "${referenceId}"
    storeDir "${referenceDir}"

    input:
        val referenceId
        val referenceDir
        val referenceUrl

    output:
        path "*"

    script:
        """
        wget -O- "$referenceUrl" | unzip -
        """
}

// nextflow run utils.nf -entry fetchRef --ref REFERENCE_ID
workflow fetchRef {
    _fetchRef(
        params.reference,
        params.bowtie2.dir,
        params.bowtie2._url
    )
}
