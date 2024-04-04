// Nextflow Wrapper to post_process.pl
process _postProcess {
    publishDir "${params.outdir}", mode: "copy", overwrite: true

    input:
        // Tuple of analysis identifiers (sample name, read ID, amplicon
        // name) and respective results directories
        // NOTE Nextflow will rename each of the input directories to
        // alleles_analysisN, where N is an increasing integer from 1
        tuple val(metas),
              path(analysisResults, name: "alleles_analysis")

    output:
        path "alleles_frequency_table.txt.gz"

    shell:
        // If there is only one analysis, then analysisResults won't be
        // an array. This code corrects that for downstream assumptions.
        // NOTE From NF 23.09, we can set the path arity to achieve this
        if (metas.size() == 1) { analysisResults = [ analysisResults ] }

        // In the workflow, the collected analyses need to be converted
        // from a list-of-meta, to a list-of-tuples, which can then be
        // transposed into a tuple-of-lists. This is so Nextflow can
        // track the results directories correctly. However, that's not
        // useful for the process, so we transpose back to form aligned
        // lists of each input.
        (sampleNames, readIds, ampliconNames) = metas
            .collect { meta -> [ meta.sampleName, meta.readId, meta.ampliconName ] }
            .transpose()

        // Make sure all our lists have the same length
        assert sampleNames.size() == readIds.size()
        assert readIds.size() == ampliconNames.size()
        assert ampliconNames.size() == analysisResults.size()

        // Template tags:
        // * sampleNames      Sample names
        // * readIds          Reads IDs
        // * ampliconNames    Amplicon names
        // * analysisResults  Respective results paths
        template "post_process.pl"
}

workflow postProcess {
    take:
        // Allele analysis channel:
        // * Metadata<Sample name, Read ID, amplicon Name>
        // * CRISPResso analysis (and convenience symlinks) directory
        alleleAnalysis

    main:
        alleleAnalysis
        | collect(flat: false, sort: { a, b -> a[0] <=> b[0] }) // Sort on metadata for stable output
        | map { it.transpose() }
        | _postProcess
}
