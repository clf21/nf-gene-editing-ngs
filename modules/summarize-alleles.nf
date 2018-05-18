// Nextflow wrapper to summarize_alleles.pl
process summarizeAlleles {
    tag "${meta.id}"
    publishDir "${params.outdir}/${meta.publishDir}", mode: "copy", overwrite: true

    // This shouldn't fail, but if it does, we don't want it to take out
    // the entire pipeline in the process!
    errorStrategy "ignore"

    input:
        // CRISPResso Allele Analysis ID and output directory
        // (i.e., Path containing CRISPResso_output/{Quantification_of_editing_frequency.txt,Frameshift_analysis.txt})
        tuple val(meta), path(analysisDir)

    output:
        // Summary of CRISPResso results
        tuple val(meta), val("results"), path("info.yaml")

    shell:
        assert meta.hasKeys(Metadata.Keys.AnalysisId)

        // Template tags:
        // * analysisDir                           Allele analysis output directory
        // * task.ext.summary.frameshiftThreshold  Percentage of frameshift reads necessary to call a Knockout genotype.
        // * task.ext.summary.hdrThreshold         Percentage of HDR reads necessary to call an HDR genotype.
        // * task.ext.summary.unmodifiedThreshold  Percentage of unmodified reads necessary to call a Wildtype genotype.
        // * task.ext.summary.wtFrameshiftMax      Maximum number of frameshift reads that can be present and still call a Wildtype genotype.
        template "summarize_alleles.pl"
}
