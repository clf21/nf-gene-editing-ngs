// Prepare (Identifier, Payload) pairs with a delimited header path for
// the purpose of YAML serialisation. That is, the header path
// represents the YAML header under the identity header, which is where
// the payload will ultimately be dumped. Examples:
//
// 1. With a sample name and a header path of "a.b.c", the stream is
//    prepared in such a way that the payload will be dumped under
//    samples.[SAMPLE NAME].a.b.c
//
// 2. With a sample ID and a header path of "a.b.c", the stream is
//    prepared in such a way that the payload will be dumped under
//    samples.[SAMPLE NAME].reads.[READ ID].a.b.c
//
// 3. With an analysis ID and a header path of "a.b.c", the stream is
//    prepared in such a way that the payload will be dumped under
//    samples.[SAMPLE NAME].reads.[READ ID].amplicons.[AMPLICON NAME].a.b.c
//
// Type introspection on the payload is used to determine whether it is
// a file or a raw value. This is to make downstream munging easier.
def _prepareYaml(meta, headerPath, payload) {
    assert meta.hasKeys(Metadata.Keys.SampleName)
    route = [ "samples", meta.sampleName ]

    if (meta.hasKeys(Metadata.Keys.ReadId)) {
        route += [ "reads", meta.readId ]

        if (meta.hasKeys(Metadata.Keys.AmpliconName)) {
            route += [ "amplicons", meta.ampliconName ]
        }
    }

    [
        (route + headerPath).join(params._internal.delimiter),
        payload
    ]
}

process _infoTableYamlFromYaml {
    input:
        // Tuple of the form:
        // * List of delimited YAML routes
        // * List of info.yaml payload paths
        tuple val(yamlRoutes),
              path(payloads, name: "*.yaml")

    output:
        path "info.yaml"

    shell:
        // If there is only one YAML route, then infoYamls won't be an
        // array. This code corrects that for downstream assumptions.
        // NOTE From NF 23.09, we can set the path arity to achieve this
        if (yamlRoutes.size() == 1) { payloads = [ payloads ] }

        // Make sure all our lists have the same length
        assert yamlRoutes.size() == payloads.size()

        // Template tags:
        // * payloadMode  Payload mode (either "raw" or "yaml")
        // * payloads     Payloads to deploy at respective route
        // * yamlRoutes   YAML routes
        payloadMode = "yaml"
        template "build_info_yaml.pl"
}

process _infoTableYamlFromRaw {
    input:
        // Tuple of the form:
        // * List of delimited YAML routes
        // * List of raw payloads
        tuple val(yamlRoutes),
              val(payloads)

    output:
        path "info.yaml"

    shell:
        // Make sure all our lists have the same length
        assert yamlRoutes.size() == payloads.size()

        // Template tags:
        // * payloadMode  Payload mode (either "raw" or "yaml")
        // * payloads     Payloads to deploy at respective route
        // * yamlRoutes   YAML routes
        payloadMode = "raw"
        template "build_info_yaml.pl"
}

process _mergeYaml {
    publishDir "${params.outdir}", mode: "copy", saveAs: { "info.yaml" }, overwrite: true

    input:
        // YAML files to merge
        path yamlFiles, name: "*.yaml"

    output:
        path "merged.yaml"

    script:
        """
        merge_yaml.pl ${yamlFiles}
        """
}

// Nextflow wrapper to info_table.pl
process _infoTable {
    publishDir "${params.outdir}", mode: "copy", saveAs: { "info.txt" }, overwrite: true

    input:
        // Info YAML Path
        // (Aggregated from info.yaml streams; see flow in generateInfoTable)
        //
        // With the following (overview) structure:
        // * samples.[SAMPLE NAME]
        //   * downsample_fastq               Downsampling info
        //   * trim_single_reads              Trimming info (single)
        //   * trim_paired_reads              Trimming info (paired)
        //   * merge_paired_reads             Merging info (paired)
        //   * reads.[READ ID]
        //     * align_reads_to_reference     Alignment info
        //     * amplicons.[AMPLICON NAME]
        //       * count_reads_overlap.count  Amplicon overlap count
        //       * summarize_alleles          Allele identification summary
        path infoYaml

    output:
        // Formatted table of results
        path "table.txt"

    shell:
        // Template tags:
        // * infoYaml             Info YAML file
        // * params._do_trimming  Trimming enabled/disabled
        // * params.merge_mode    Merge mode
        // * task.ext.maxReads    Read count threshold
        template "info_table.pl"
}

workflow toOverlapInfo {
    take:
        // NOTE These inputs are precisely the output of countReadOverlap

        // Channel of tuples, of the form:
        // * Metadata<Sample name, Read ID, Amplicon name>
        // * Aligned BAM and its index
        // * Path to amplicon definition
        // * Overlap count
        identifiedCount

        // Channel of tuples, of the form:
        // * Metadata<Sample name, Read ID, Amplicon name>
        // * Aligned BAM and its index
        // * Path to amplicon definition
        // * Overlap count
        skippedCount

    main:
        identifiedCount
        | mix(skippedCount)
        | map { meta, _bam, _ampliconYaml, overlapCount -> [ meta, overlapCount ] }
        | set { overlap }

    emit:
        // Channel of tuples, of the form:
        // * Metadata<Sample name, Read ID, Amplicon name>
        // * Overlap count
        overlap
}

workflow toResultsInfo {
    take:
        // NOTE This input is precisely the output of summarizeAlleles

        // Channel of tuples, of the form:
        // * Metadata<Sample name, Read ID>
        // * Output type
        // * Path to output summary
        summarizedAnalyses

    main:
        summarizedAnalyses
        | map { meta, _outputType, output -> [ meta, output ] }
        | set { results }

    emit:
        // Channel of tuples, of the form:
        // * Metadata<Sample name, Read ID>
        // * Path to result info
        results
}

workflow generateInfoTable {
    take:
        // Channel of tuples, of the form:
        // * Metadata<Sample name>
        // * Path to downsampling info (1 or 2, for single and paired reads, respectively)
        downsampledInfo

        // Channel of tuples, of the form:
        // * Metadata<Sample name>
        // * Path to trimming info
        trimmedSingleInfo

        // Channel of tuples, of the form:
        // * Metadata<Sample name>
        // * Path to trimming info
        trimmedPairedInfo

        // Channel of tuples, of the form:
        // * Metadata<Sample name>
        // * Path to merge info
        mergedInfo

        // Channel of tuples, of the form:
        // * Metadata<Sample name, Read ID>
        // * Path to alignment info
        alignedInfo

        // Channel of tuples, of the form:
        // * Metadata<Sample name, Read ID, Amplicon name>
        // * Overlap count
        overlapInfo

        // Channel of tuples, of the form:
        // * Metadata<Sample name, Read ID, Amplicon name>
        // * Path to result info
        resultsInfo

    main:
        // The majority of the info streams involve YAML files. Prepare
        // these individually and then combine to feed into the YAML
        // builder process...
        downsampledInfo
        | flatMap { meta, info ->
            if (info instanceof ArrayList) {
                // If `info` is an array, then we have paired reads...
                return [
                    _prepareYaml(meta, ["downsample_fastq", "read_1"], info[0]),
                    _prepareYaml(meta, ["downsample_fastq", "read_2"], info[1])
                ]

            } else {
                // ...otherwise, we have single reads
                return [
                    _prepareYaml(meta, ["downsample_fastq", "read_1"], info)
                ]
            }
        }
        | set { preparedDownsampledInfo }

        trimmedSingleInfo
        | map { meta, info -> _prepareYaml(meta, ["trim_single_reads"], info) }
        | set { preparedTrimmedSingleInfo }

        trimmedPairedInfo
        | map { meta, info -> _prepareYaml(meta, ["trim_paired_reads"], info) }
        | set { preparedTrimmedPairedInfo }

        mergedInfo
        | map { meta, info -> _prepareYaml(meta, ["merge_paired_reads"], info) }
        | set { preparedMergedInfo }

        alignedInfo
        | map { meta, info -> _prepareYaml(meta, ["align_reads_to_reference"], info) }
        | set { preparedAlignedInfo }

        resultsInfo
        | map { meta, info -> _prepareYaml(meta, ["summarize_alleles"], info) }
        | set { preparedResultsInfo }

        preparedDownsampledInfo
        | mix(
            preparedTrimmedSingleInfo,
            preparedTrimmedPairedInfo,
            preparedMergedInfo,
            preparedAlignedInfo,
            preparedResultsInfo
        )
        | collect(flat: false)
        | map { it.transpose() }
        | _infoTableYamlFromYaml
        | set { infoFromYaml }

        // ...Some of the info streams, however, involve raw values.
        // These need to be prepared separately to feed into the YAML
        // builder process...
        // (This is necessary because, when we collect and transpose the
        // streams, we can choose to end up with either: a heterogeneous
        // list of files and raw values; or two lists, one with files
        // and the other with raw values, that may contain nulls.
        // Neither option is acceptable to Nextflow, so we *have* to
        // split and process them separately.)
        overlapInfo
        | map { meta, overlap -> _prepareYaml(meta, ["count_reads_overlap", "count"], overlap) }
        | set { preparedOverlapInfo }

        preparedOverlapInfo
        | collect(flat: false)
        | map { it.transpose() }
        | _infoTableYamlFromRaw
        | set { infoFromRaw }

        // ...Finally, we can merge the two built YAML files into one!
        infoFromYaml
        | combine(infoFromRaw)
        | _mergeYaml
        | _infoTable
}
