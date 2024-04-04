// Nextflow wrapper to identify_alleles.pl
process identifyAllelesCRISPResso {
    label "process_low"
    label "usesSamtools"
    label "usesCRISPResso"

    errorStrategy "ignore"

    tag "${meta.id}"
    publishDir "${params.outdir}", mode: "copyNoFollow", overwrite: true, saveAs: {
        it == "alleles_analysis" ? meta.publishDir : it
    }

    input:
        // Tuple of:
        // * Metadata<Sample name, Read ID, Amplicon name>
        // * Aligned sample read (BAM and index) paths
        // * Amplicon description (YAML) path
        // * Amplicon read overlap count
        //
        // The Amplicon YAML should have the following structure:
        // * name  String  Name of the amplicon
        // * info  Object  Amplicon alignment details
        tuple val(meta),
              path(alignedSample),
              path(ampliconYaml),
              val(overlapCount)

    output:
        // * Identifier, of the form Metadata<Sample name, Read ID, Amplicon name>
        // * CRISPResso analysis (and convenience symlinks) directory
        tuple val(meta),
              path("alleles_analysis")

    shell:
        assert meta.hasKeys(Metadata.Keys.AnalysisId)

        assert alignedSample.size() == 2
        (sampleBam, _) = alignedSample

        // Template Tags:
        // * ampliconYaml                  Amplicon YAML file
        // * meta.readId                   Read ID
        // * meta.sampleName               Sample name
        // * overlapCount                  Amplicon read overlap count
        // * sampleBam                     BAM of aligned sample read
        // * task.ext.maxReadsPerAmplicon  Maximum amplicon read overlap count
        // * task.ext.crispresso           CRISPResso command definition
        // * task.ext.crispresso.window    Window (bp) around sgRNA
        // * task.ext.samtools             Samtools command definition
        template "identify_alleles_crispresso.pl"
}

process reportFailedAnalysis {
    tag "${meta.id}"
    publishDir "${params.outdir}/${meta.publishDir}", mode: "copy", overwrite: true

    input:
        // Failed analysis identifier
        // (i.e., Meta<Sample name, Read ID, Amplicon name>)
        val meta

    output:
        tuple val(meta),
              val("failed"),
              path("error.yaml")

    shell:
        assert meta.hasKeys(Metadata.Keys.AnalysisId)

        '''
        #!/usr/bin/env perl

        use strict;
        use warnings;
        use YAML::XS qw(DumpFile);

        DumpFile("error.yaml", ["Check Nextflow logs for details"]);
        '''
}

workflow identifyAlleles {
    take:
        // Channel of read, amplicon and overlap count. That is, tuples
        // of the form:
        // * Meta<Sample name, Read ID, Amplicon name>
        // * Aligned sample read (and corresponding index)
        // * Amplicon description YAML
        // * Overlap count
        //
        // i.e., The output of each countReadOverlap branch
        readsWithAmpliconsAndOverlapCount

    main:
        readsWithAmpliconsAndOverlapCount
        | identifyAllelesCRISPResso
        | set { passedAnalyses }

        // To determine failures, we only care about the input
        // identifiers; so drop everything else
        readsWithAmpliconsAndOverlapCount
        | map { meta, _sample, _ampliconYaml, _overlap -> meta }
        | set { inputIdentifiers }

        // NOTE If _no_ analyses succeeded, then the result of the join
        // will just be inputIdentifiers; hence the filter condition.
        // That is, if the input is a Metadata object, then all analyses
        // must have failed, otherwise we search for those that matched
        // in the outer join.
        inputIdentifiers
        | join(passedAnalyses, remainder: true)
        | filter { (it instanceof Metadata) || (it[1] == null) }
        | map { it instanceof Metadata ? it : it[0] }
        | set { failedAnalyses }

    emit:
        // Channel of tuples of the form:
        // * Metadata<Sample name, Read ID, Amplicon name>
        // * CRISPResso analysis (and convenience symlinks) directory
        passed = passedAnalyses

        // Channel of failed Metadata<Sample name, Read ID, Amplicon name>
        failed = failedAnalyses
}
