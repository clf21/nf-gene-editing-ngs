workflow listSequencingSamples {
    take:
        // State trigger to wait for deployment
        isDeployed

        // Path to sequencing samples
        samplePath

    main:
        // NOTE We have to combine the isDeployed channel for
        // synchronisation purposes; hence the useless-looking
        // combine-then-map. It is otherwise functionally redundant.
        Channel.fromFilePairs("${samplePath}/*", size: -1) { new Metadata(it, params.samples_pattern) }
        | combine(isDeployed)
        | map { meta, samples, _deployed -> [ meta, samples ] }
        | filter { meta, samples -> params.samplesFilter.filter(meta.sampleName, samples) }
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
