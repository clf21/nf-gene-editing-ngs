// Nextflow wrapper to count_reads_overlap.pl
process _countReadsOverlap {
    label "usesSamtools"

    tag "${metaWithAmplicon.id}"

    input:
        // Tuple of:
        // * Metadata<Sample name, Read ID>
        // * Aligned sample read (BAM and index) paths
        // * Amplicon description (YAML) path
        //
        // The Amplicon YAML should have the following structure:
        // * name  String  Name of the amplicon
        // * info  Object  Amplicon alignment details
        tuple val(meta), path(alignedSample), path(ampliconYaml)

    output:
        // Append the count of reads overlapping the region
        // NOTE stdout will be interpreted as a string, so it needs to
        // be converted to an integer downstream
        tuple val(meta), path(alignedSample), path(ampliconYaml), stdout

    shell:
        assert meta.hasKeys(Metadata.Keys.SampleId)
        metaWithAmplicon = meta << ampliconYaml

        assert alignedSample.size() == 2
        (sampleBam, _) = alignedSample

        // Template Tags:
        // * ampliconYaml       Amplicon YAML file
        // * sampleBam          BAMfile of aligned sample read
        // * task.ext.samtools  Samtools command definition
        template "count_reads_overlap.pl"
}

process _reportSkippedSamples {
    tag "${skippedMeta.id}"
    publishDir "${params.outdir}/${skippedMeta.publishDir}", mode: "copy", overwrite: true

    input:
        // Tuple of
        // * Skipped Metadata<Sample name, Read ID, Amplicon name>
        // * The number of overlapping reads
        tuple val(skippedMeta), val(overlappingReads)

    output:
        tuple val(skippedMeta), val("skipped"), path("skipped.yaml")

    shell:
        assert skippedMeta.hasKeys(Metadata.Keys.AnalysisId)

        '''
        #!/usr/bin/env perl

        use strict;
        use warnings;
        use YAML::XS qw(DumpFile);

        DumpFile("skipped.yaml", [{
            insufficient_overlap => !{overlappingReads}
        }]);
        '''
}

workflow countReadOverlap {
    take:
        // Read and Amplicon Channel
        // Of the form [Metadata<Sample name, Read ID>, [Aligned Read BAM, BAM Index], Amplicon YAML]
        readsWithAmplicons

    main:
        _countReadsOverlap(readsWithAmplicons)
        | map { meta, bam, amplicon, overlapCount -> [ meta, bam, amplicon, overlapCount as Integer ] }
        | branch { _meta, _bam, _amplicon, overlapCount ->
            toSkip:     overlapCount < params.minReadsPerAmplicon
            toIdentify: true // Everything else
        }
        | set { counted }

    emit:
        toSkip = counted.toSkip
        toIdentify = counted.toIdentify
}

workflow reportSkippedSamples {
    take:
        // Read, Amplicon, Overlap Count Channel
        // Of the form [Metadata<Sample name, Read ID>, [Aligned Read BAM, BAM Index], Amplicon YAML, Overlap Count]
        // i.e., The output of each countReadOverlap branch
        readsWithAmpliconsAndOverlapCount

    main:
        readsWithAmpliconsAndOverlapCount
        | map { meta, _bam, ampliconYaml, overlapCount -> [ meta << ampliconYaml, overlapCount ] }
        | _reportSkippedSamples
        | set { skippedSamples }

    emit:
        skippedSamples
}
