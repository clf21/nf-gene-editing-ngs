process _countGuidePositionBases {
    label "process_low"
    label "usesComputeBaseCounts"

    tag "${meta.id()}"
    publishDir "${params.outdir}/${meta.publishDir()}/base-counts", mode: "copy", overwrite: true

    input:
        // Tuple of:
        // * Metadata<Sample name, Read ID, Amplicon name>
        // * Aligned sample read (BAM and index) paths
        // * Amplicon description (YAML) path
        //
        // The Amplicon YAML should have the following structure:
        // * name  String  Name of the amplicon
        // * info  Object  Amplicon alignment details
        tuple val(meta),
              path(alignedSample),
              path(ampliconYaml)

    output:
        // * Identifier, of the form Metadata<Sample name, Read ID, Amplicon name>
        // * Guild position base counts
        tuple val(meta),
              path("*_baseCounts.txt"),
              emit: baseCounts,
              optional: true

        path "*.yaml", emit: exception, optional: true

    shell:
        assert meta.hasKeys(Metadata.Keys.AnalysisId)

        assert alignedSample.size() == 2
        (sampleBam, _) = alignedSample

        samplePrefix = meta.sampleName
        if (meta.readId != "Merged") { samplePrefix += "_${meta.readId}" }

        // Template Tags:
        // * ampliconYaml                             Amplicon YAML file
        // * sampleBam                                BAM of aligned sample read
        // * samplePrefix                             Sample name and read ID (see above)
        // * task.ext.computeBaseCounts               Base editing command definition
        // * task.ext.computeBaseCounts.windowAfter   Window (bp) after sgRNA
        // * task.ext.computeBaseCounts.windowBefore  Window (bp) before sgRNA
        template "count_bases_by_guide_position.sh"
}

process _publishBaseCounts {
    label "process_low"

    publishDir "${params.outdir}", mode: "copy", overwrite: true

    input:
        // NOTE It is expected that these inputs (i.e., a list of files)
        // are given in a stable order to ensure stable output.
        path baseCounts, name: "?.txt"

    output:
        path "guide_position_base_counts.txt"

    shell:
        // Ensure baseCounts is a list, if we only have one
        // NOTE The `arity` parameter, introduced in NF 23.09, can be
        // used to supersede this manual check.
        if (baseCounts !instanceof List) { baseCounts = [ baseCounts ] }

        // Handle empty list case
        if (baseCounts.isEmpty()) {
            '''
            # No base counts available (tool not installed or all failed)
            echo -e "Sample\\tAmplicon\\tReadName\\tPosition\\tBase\\tCount" > guide_position_base_counts.txt
            '''
        } else {
            '''
            # Output first file in full
            cp "!{baseCounts.head()}" guide_position_base_counts.txt

            # Concatenate subsequent files, without the header
            declare -a TAIL=(!{baseCounts.tail().join(" ")})
            for FILE in "${TAIL[@]}"; do
              sed 1d "$FILE" >> guide_position_base_counts.txt
            done
            '''
        }
}

workflow countGuidePositionBases {
    take:
        // Channel of read, amplicon and overlap count. That is, tuples
        // of the form:
        // * Metadata<Sample name, Read ID, Amplicon name>
        // * Aligned sample read (and corresponding index)
        // * Amplicon description YAML
        // * Overlap count
        //
        // i.e., The output of each countReadOverlap branch
        readsWithAmpliconsAndOverlapCount

    main:
        readsWithAmpliconsAndOverlapCount
        | map { meta, bam, amplicon, _overlap -> [ meta, bam, amplicon ] }
        | _countGuidePositionBases
        | set { output }

    emit:
        // NOTE Only the base counts are needed downstream
        output.baseCounts
}

workflow collectBaseCounts {
    take:
        // Channel of guide position base counts. That is, tuples of the
        // form:
        // * Metadata<Sample name, Read ID, Amplicon name>
        // * Guide position base count file
        //
        // i.e., The output of countGuidePositionBases
        baseCounts

    main:
        baseCounts
        | collect(flat: false, sort: { a, b -> a[0] <=> b[0] }) // Sort by metadata for stable output
        | map { it.transpose()[1] }
        | ifEmpty([])
        | _publishBaseCounts
}
