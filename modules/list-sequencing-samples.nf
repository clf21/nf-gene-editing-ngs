workflow listSequencingSamples {
    take:
        // Path to sequencing samples
        samplePath

    main:
        Channel.fromFilePairs("${samplePath}/*", size: -1) { new Metadata(it, params.samplesPattern) }
        | filter { meta, samples -> params.samplesFilter.filter(meta.sampleName, samples) }
        | set { samples }

        samples
        | subscribe { meta, samples ->
            // Fail if we don't have single or paired samples
            if (samples.size() > 2) {
                Utils.usage(params, samples.inject(
                    "Non-single or non-paired reads detected; '${meta.sampleName}' has ${samples.size()} reads!",
                    { msg, sample -> "${msg}\n* ${sample.name}" }
                ))
            }

            // Fail if the sample name contains any forbidden character
            if (params._internal.forbidden.any { meta.sampleName.contains(it) }) {
                Utils.usage(params, "Sample detected with an invalid name; '${meta.sampleName}' contains forbidden characters!")
            }
        }

    emit:
        samples
}
