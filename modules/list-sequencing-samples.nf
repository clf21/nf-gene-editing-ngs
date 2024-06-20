workflow listSequencingSamples {
    take:
        // State trigger to wait for deployment
        isDeployed

        // Path to sequencing samples
        samplePath

        // Channel of sample metadata
        sampleMetadata

    main:
        // Because of the way the join works, we need to construct a
        // channel that will give us the information we need to merge
        // what metadata we've been provided downstream.
        sampleMetadata
        | map { meta -> [ meta.sampleName, meta ] }
        | set { sampleMetadataForJoin }

        // NOTE We have to combine the isDeployed channel for
        // synchronisation purposes; hence the useless-looking
        // combine-then-map. It is otherwise functionally redundant.
        Channel.fromFilePairs("${samplePath}/*", size: -1) { Utils.getSampleName(it, params.samples_pattern) }
        | combine(isDeployed)
        | map { sampleName, samples, _deployed -> [ sampleName, samples ] }
        | filter { sampleName, samples -> params.samplesFilter.filter(sampleName, samples) }
        | join(sampleMetadataForJoin, remainder: true)
        | filter { _sampleName, samples, _metadata -> samples != null }
        | map { sampleName, samples, metaWithMetadata ->
            // Take the richest source of metadata and augment it with
            // the sample input FASTQ files
            meta = metaWithMetadata ?: new Metadata(sampleName)
            meta.sampleMetadata.sampleFastqs = samples.collect { "${samplePath}/${it.name}" }

            [ meta, samples ]
        }
        | set { samples }

        samples
        | subscribe { meta, samples ->
            // Fail if we don't have single or paired samples
            if (samples.size() > 2) {
                Utils.usage(workflow, params, samples.inject(
                    "Non-single or non-paired reads detected; '${meta.sampleName}' has ${samples.size()} reads!",
                    { msg, sample -> "${msg}\n* ${sample.name}" }
                ))
            }

            // Fail if the sample name contains any forbidden character
            if (params._internal.forbidden.any { meta.sampleName.contains(it) }) {
                Utils.usage(workflow, params, "Sample detected with an invalid name; '${meta.sampleName}' contains forbidden characters!")
            }
        }

    emit:
        samples
}
