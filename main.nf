// NOTE Preflight *must* be done before module imports
Utils.preFlight(params)

include { alignAmpliconsToReference } from "./modules/align-amplicons-to-reference"
include { alignReadsToReference } from "./modules/align-reads-to-reference"
include { countReadOverlap; reportSkippedSamples } from "./modules/count-reads-overlap"
include { determineReferenceCoordinates } from "./modules/determine-reference-coordinates"
include { downsampleSingleReads; downsamplePairedReads } from "./modules/downsample"
include { generateInfoTable; toOverlapInfo; toResultsInfo } from "./modules/info-table"
include { generateSummaryTable } from "./modules/summary-table"
include { identifyAlleles; reportFailedAnalysis } from "./modules/identify-alleles"
include { listSequencingSamples } from "./modules/list-sequencing-samples"
include { mergePairedReads } from "./modules/merge-paired-reads"
include { normalizeAmplicons } from "./modules/normalize-amplicons"
include { postProcess } from "./modules/post-process"
include { splitAmpliconsYaml } from "./modules/split-amplicons-yaml"
include { summarizeAlleles } from "./modules/summarize-alleles"
include { trimPairedReads } from "./modules/trim-paired-reads"
include { trimSingleReads } from "./modules/trim-single-reads"

workflow {
    // Prepare amplicons
    normalizeAmplicons(file(params.ampliconsYaml))
    | alignAmpliconsToReference
    | determineReferenceCoordinates
    | splitAmpliconsYaml
    | set { amplicons }

    // Acquire input samples
    listSequencingSamples(params.fastqDir)
    | branch { _name, samples ->
        single: samples.size() == 1
        paired: samples.size() == 2
    }
    | set { samples }

    // Prepare sample reads (single)
    downsampledSingle = samples.single | downsampleSingleReads
    trimmedSingle = downsampledSingle.output | trimSingleReads

    // Prepare sample reads (paired)
    downsampledPaired = samples.paired | downsamplePairedReads
    trimmedPaired = downsampledPaired.output | trimPairedReads
    mergedPaired = trimmedPaired.output | mergePairedReads

    trimmedSingle.output
    | mix(mergedPaired.output)
    | alignReadsToReference
    | set { aligned }

    // Combine samples and amplicons and bucket by the overlap threshold
    aligned.output
    | combine(amplicons)
    | countReadOverlap
    | set { counted }

    // Run analysis on sample/amplicon pairs with sufficient overlap
    counted.toIdentify
    | identifyAlleles
    | set { alleleAnalyses }

    // Report insufficiently overlapping sample/amplicon pairs
    counted.toSkip
    | reportSkippedSamples
    | set { skippedAnalyses }

    // Report failed allele analyses
    alleleAnalyses.failed
    | reportFailedAnalysis
    | set { failedAnalyses }

    // Summarise all output
    alleleAnalyses.passed
    | summarizeAlleles
    | set { summarizedAnalyses }

    summarizedAnalyses
    | mix(skippedAnalyses, failedAnalyses)
    | generateSummaryTable

    aligned.failed
    | view { meta -> "\033[0;31mRead alignment failure for sample ${meta.id}!\033[0m" }

    generateInfoTable(
        downsampledSingle.info | mix(downsampledPaired.info),
        trimmedSingle.info,
        trimmedPaired.info,
        mergedPaired.info,
        aligned.info,
        counted | toOverlapInfo,
        summarizedAnalyses | toResultsInfo
    )

    // Summarise identified allele frequency tables
    alleleAnalyses.passed
    | postProcess
}
