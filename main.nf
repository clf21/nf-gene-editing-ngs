// NOTE Preflight *must* be done before module imports
Utils.preFlight(workflow, params)

include { acquireMetadata } from "./modules/acquire-metadata"
include { alignAmpliconsToReference } from "./modules/align-amplicons-to-reference"
include { alignReadsToReference } from "./modules/align-reads-to-reference"
include { collectAlleleFrequencies } from "./modules/collect-allele-frequencies"
include { countGuidePositionBases; collectBaseCounts } from "./modules/count-bases-by-guide-position"
include { countReadOverlap; reportSkippedSamples } from "./modules/count-reads-overlap"
include { deploy } from "./deploy"
include { determineReferenceCoordinates } from "./modules/determine-reference-coordinates"
include { downsampleSingleReads; downsamplePairedReads } from "./modules/downsample"
include { generateInfoTable; toOverlapInfo; toResultsInfo } from "./modules/info-table"
include { generateSummaryTable } from "./modules/summary-table"
include { identifyAlleles; reportFailedAnalysis } from "./modules/identify-alleles"
include { listSequencingSamples } from "./modules/list-sequencing-samples"
include { mergePairedReads } from "./modules/merge-paired-reads"
include { normalizeAmplicons } from "./modules/normalize-amplicons"
include { publishMetadata; toSampleMetadata } from "./modules/publish-metadata"
include { selectAmplicons } from "./modules/select-amplicons"
include { splitAmpliconsYaml } from "./modules/split-amplicons-yaml"
include { summarizeAlleles } from "./modules/summarize-alleles"
include { trimPairedReads } from "./modules/trim-paired-reads"
include { trimSingleReads } from "./modules/trim-single-reads"

workflow {
    // Deploy environment
    deploy
    | set { isDeployed }

    // Prepare sample and experiment metadata
    acquireMetadata(params.metadata)
    | set { metadata }

    // Prepare amplicons
    normalizeAmplicons(isDeployed, file(params.amplicons))
    | alignAmpliconsToReference
    | determineReferenceCoordinates
    | set { preparedAmplicons }

    preparedAmplicons
    | splitAmpliconsYaml
    | set { amplicons }

    // Acquire input samples
    listSequencingSamples(isDeployed, params.fastq_dir, metadata.samples)
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

    // Combine samples and amplicons, then select those which have
    // either been specified in the metadata, or all combinations
    // otherwise. Finally, bucket unspecified combinations by the
    // overlap threshold (i.e., specified combinations are analysed
    // regardless).
    selectAmplicons(aligned.output, amplicons)
    | countReadOverlap
    | set { readOverlapCounted }

    // Run analysis on sample/amplicon pairs with sufficient overlap
    readOverlapCounted.toIdentify
    | identifyAlleles
    | set { alleleAnalyses }

    // Calculate guide position base counts for sample/amplicon pairs with sufficient overlap
    readOverlapCounted.toIdentify
    | countGuidePositionBases
    | set { guidePositionBaseCounts }

    // Report insufficiently overlapping sample/amplicon pairs
    readOverlapCounted.toSkip
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
    | view { meta -> "\033[0;31mRead alignment failure for sample ${meta.id()}!\033[0m" }

    generateInfoTable(
        downsampledSingle.info | mix(downsampledPaired.info),
        trimmedSingle.info,
        trimmedPaired.info,
        mergedPaired.info,
        aligned.info,
        readOverlapCounted | toOverlapInfo,
        summarizedAnalyses | toResultsInfo
    )

    // Publish metadata
    publishMetadata(
        // NOTE While Nextflow will automatically unpack `samples`, we
        // do so explicitly here to make the inputs clearer
        toSampleMetadata(samples.single, samples.paired, alleleAnalyses.passed),

        metadata.experiment,
        preparedAmplicons
    )

    // Summarise identified allele frequency tables
    alleleAnalyses.passed
    | collectAlleleFrequencies

    // Summarise guide position base counts
    guidePositionBaseCounts
    | collectBaseCounts
}
