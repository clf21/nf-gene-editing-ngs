// Nextflow wrapper to count_reads_overlap.pl
process _countReadsOverlap {
    label "usesSamtools"

    tag "${N} Combinations"

    input:
        // Tuple of:
        // * List of Metadata<Sample name, Read ID, Amplicon name>
        // * List of aligned sample BAM files
        // * List of associated BAM index files
        // * List of amplicon description YAML files
        //
        // Each amplicon YAML should have the following structure:
        // * name  String  Name of the amplicon
        // * info  Object  Amplicon alignment details
        //
        // NOTE The input files will be renamed by Nextflow to N.*, for
        // each extension, where N is an increasing integer from 1.
        tuple val(metas),
              path(alignedBams,   name: "?.bam"),
              path(bamIndices,    name: "?.bam.bai"),
              path(ampliconYamls, name: "?.yaml")

    output:
        // Append the count of reads overlapping the region
        // NOTE stdout will be need to be processed downstream
        tuple val(metas),
              path(alignedBams),
              path(bamIndices),
              path(ampliconYamls),
              stdout

    shell:
        N = metas.size()

        // If there is only one input, then the file inputs won't be
        // arrays. This code corrects that for downstream assumptions.
        // NOTE From NF 23.09, we can set the path arity to achieve this
        if (N == 1) {
            alignedBams   = [ alignedBams ]
            bamIndices    = [ bamIndices ]
            ampliconYamls = [ ampliconYamls ]
        }

        // Make sure all our lists have the same length
        assert N == alignedBams.size()
        assert alignedBams.size() == bamIndices.size()
        assert bamIndices.size() == ampliconYamls.size()

        // Template Tags:
        // * N                  Number of items
        // * task.ext.samtools  Samtools command definition
        template "count_reads_overlap.pl"
}

process _reportSkippedSamples {
    tag "${skippedMetas.size()} Combinations"

    publishDir "${params.outdir}", mode: "copy", overwrite: true, saveAs: {
        // The output filenames are N.yaml, where N matches the index
        // over skippedMetas, so we extract it to get the publishDir
        idx = (it - ".yaml") as Integer
        "${skippedMetas[idx].publishDir}/skipped.yaml"
    }

    input:
        // Tuple of
        // * List of skipped Metadata<Sample name, Read ID, Amplicon name>
        // * List of overlap counts
        tuple val(skippedMetas),
              val(overlapCounts)

    output:
        // NOTE This relies on Nextflow's globbing returning files in
        // lexicographic order, to maintain alignment with skippedMetas
        tuple val(skippedMetas),
              path("*.yaml")

    shell:
        // Make sure our lists have the same length
        assert skippedMetas.size() == overlapCounts.size()

        '''
        #!/usr/bin/env perl

        use strict;
        use warnings;
        use YAML::XS qw(DumpFile);

        my @counts = qw(!{overlapCounts.join(" ")});

        foreach my $i (0 .. !{skippedMetas.size() - 1}) {
            # Zero pad the filename so order is maintained
            my $idx = sprintf "%08d", $i;

            DumpFile("${idx}.yaml", [{
                insufficient_overlap => $counts[$i] + 0
            }]);
        }
        '''
}

workflow countReadOverlap {
    take:
        // Read and Amplicon Channel
        // Tuples of the form:
        // * Metadata<Sample name, Read ID>
        // * [Aligned Read BAM, BAM Index]
        // * Amplicon YAML
        readsWithAmplicons

    main:
        // Read-amplicon overlap counting is dwarfed by the process
        // creation overhead, so we batch all samples * amplicons inputs
        // into a single input. To do so, we have to munge the inputs
        // into an acceptable form, such that Nextflow will track files
        // correctly. Specifically:
        //
        // 1a. Augment the sample identifier metadata with the amplicon
        //     name (i.e., to make it an analysis identifier).
        //  b. Split out the aligned BAM and its index into individual
        //     elements in the input (i.e., flattening the input).
        // 2.  Collect each input into a single list-of-tuples.
        // 3.  Transpose this into tuples of lists; that is, the single
        //     input to the process will be a tuple containing:
        //     * A list of analysis identifiers
        //     * A list of aligned read BAM files
        //     * A list of the associated BAM index files
        //     * A list of amplicon description YAML files
        //
        // _countReadsOverlap returns the same input, with the counts
        // appended as a delimited string. This is split and the output
        // re-transposed and reassembled in a flatMap to "unbatch".
        readsWithAmplicons
        | map { meta, alignedRead, ampliconYaml -> [ meta << ampliconYaml, alignedRead[0], alignedRead[1], ampliconYaml ] }
        | collect(flat: false)
        | map { it.transpose() }
        | _countReadsOverlap
        | flatMap { metas, alignedBams, bamIndices, ampliconYamls, counts ->
            // Split delimited counts and cast to integers
            counts = counts.tokenize(params._internal.delimiter)
                           .collect { it as Integer }

            [ metas, alignedBams, bamIndices, ampliconYamls, counts ]
                .transpose()
                .collect { meta, alignedBam, bamIndex, ampliconYaml, overlapCount ->
                    // Put the BAM and its index back together
                    [ meta, [alignedBam, bamIndex], ampliconYaml, overlapCount ]
                }
        }
        | branch { _meta, _bam, _amplicon, overlapCount ->
            toSkip:     overlapCount < params.min_reads_per_amplicon
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
        // Of the form [Metadata<Sample name, Read ID, Amplicon name>, [Aligned Read BAM, BAM Index], Amplicon YAML, Overlap Count]
        // i.e., The output of each countReadOverlap branch
        readsWithAmpliconsAndOverlapCount

    main:
        // Batch:   collect and transpose
        // Unbatch: transpose and collect
        readsWithAmpliconsAndOverlapCount
        | collect(flat: false)
        | map {
            (metas, _bams, _ampliconYamls, overlapCounts) = it.transpose()
            [ metas, overlapCounts ]
        }
        | _reportSkippedSamples
        | flatMap { metas, skippedYamls ->
            // If there is only one output, then the file output won't
            // be an array. This code corrects that for downstream.
            // NOTE From NF 23.09, we can set the path arity to achieve this
            if (metas.size() == 1) { skippedYamls = [ skippedYamls ] }

            [ metas, skippedYamls ]
                .transpose()
                .collect { meta, skippedYaml -> [ meta, "skipped", skippedYaml ] }
        }
        | set { skippedSamples }

    emit:
        skippedSamples
}
