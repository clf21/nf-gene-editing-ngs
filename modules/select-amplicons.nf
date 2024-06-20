workflow selectAmplicons {
    take:
        // Channel of samples. That is, tuples of the form:
        // * Metadata<Sample name, Read ID>
        // * Aligned sample BAM (and its index)
        samples

        // Channel of amplicons. That is, tuples of the form:
        // * Amplicon name
        // * Amplicon description YAML
        amplicons

    main:
        // Split samples by those with user-specified amplicons, and
        // those without
        samples
        | branch { meta, _bam ->
            userSpecified: meta.sampleMetadata.amplicons.size() > 0
            allCombos: true
        }
        | set { bucketedSamples }

        // Join the samples with user-specified amplicons with the known
        // amplicons. (NOTE We have the do a cross product and filter,
        // because join expects unique keys.)
        bucketedSamples.userSpecified
        | flatMap { meta, bam ->
            meta.sampleMetadata.amplicons.collect { metaAmpliconName ->
                [ metaAmpliconName, meta.clone(), bam ]
            }
        }
        | combine(amplicons)
        | filter { metaAmpliconName, _meta, _bam, ampliconName, _ampliconYaml -> metaAmpliconName == ampliconName }
        | map { _metaAmpliconName, meta, bam, ampliconName, ampliconYaml ->
            // Set the last field to true: i.e., user-specified
            [ meta.cloneWithAmpliconName(ampliconName), bam, ampliconYaml, true ]
        }
        | set { userCombos }

        // Combine the remaining samples with all known amplicons
        bucketedSamples.allCombos
        | combine(amplicons)
        | map { meta, bam, ampliconName, ampliconYaml ->
            // Set the last field to false: i.e., not user-specified
            [ meta.cloneWithAmpliconName(ampliconName), bam, ampliconYaml, false ]
        }
        | set { allCombos }

        userCombos
        | mix(allCombos)
        | set { sampleAmpliconPairs }

    emit:
        // Channel of sample/amplicon pairs. That is, tuples of the
        // form:
        // * Metadata<Sample name, Read ID, Amplicon name>
        // * Aligned sample BAM (and its index)
        // * Amplicon description YAML
        // * Whether the combination is user-specified
        sampleAmpliconPairs
}
