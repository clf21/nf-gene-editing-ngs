// Nextflow Wrapper to downsample_fastq.pl
process _downsampleSingleRead {
  tag "${meta.id}"

  input:
    // Single read tuple: Metadata<Sample name> and its FASTQ file
    tuple val(meta),
          path(fastqFile)

  output:
    // Downsampled read
    tuple val(meta),
          val(sortKey),
          path("downsampled.fastq.gz"),
          emit: output

    // Additional details about downsampling
    tuple val(meta),
          val(sortKey),
          path("info.yaml"),
          emit: info

  shell:
    // We use the input FASTQ file's basename as a sort key downstream
    sortKey = fastqFile.name

    // Template tags:
    // * fastqFile          FASTQ file of sample read
    // * task.ext.maxReads  Maximum number of reads to keep after downsampling
    // * task.ext.seed      Seed for the pseudorandom number generator
    template "downsample_fastq.pl"
}

// Generic downsampling workflow, with ancillary output for the sake of
// downstream processing. Do not use this workflow directly; use either
// downsampleSingleReads or downsamplePairedReads, as appropriate.
workflow _downsample {
    take:
        // Channel of tuples, of the form:
        // * Metadata<Sample name>
        // * FASTQ path
        reads

    main:
        if (params.maxReads) {
            // Only downsample if maxReads is set...
            reads
            | _downsampleSingleRead
            | set { downsampled }

            samples = downsampled.output
            info = downsampled.info

        } else {
            // ...otherwise, bypass downsampling
            reads
            | map { meta, fastq -> [ meta, fastq.name, fastq ] }
            | set { samples }

            info = Channel.empty()
        }

    emit:
        // Channel of tuples, of the form:
        // * Metadata<Sample name>
        // * Sort key
        // * Downsampled read
        output = samples

        // Channel of tuples, of the form:
        // * Metadata<Sample name>
        // * Sort key
        // * Downsampling info
        info = info
}

workflow downsampleSingleReads {
    take:
        // Single Read Channel
        // Tuples of (Metadata<Sample Name>, FASTQ Path)
        singleReads

    main:
        singleReads
        | _downsample
        | set { downsampled }

        // Remove the (redundant) sort key from the output
        downsampled.output
        | map { meta, _sortKey, downsampled -> [ meta, downsampled ] }
        | set { samples }

        downsampled.info
        | map { meta, _sortKey, info -> [ meta, info ] }
        | set { info }

    emit:
        // Channel of tuples, of the form:
        // * Metadata<Sample name>
        // * Downsampled read
        output = samples

        // Channel of tuples, of the form:
        // * Metadata<Sample name>
        // * Downsampling info
        info = info
}

workflow downsamplePairedReads {
    take:
        // Paired Read Channel
        // Tuples of (Metadata<Sample Name>, [FASTQ_1 Path, FASTQ_2 Path])
        pairedReads

    main:
        // Transpose the paired reads so they can be run through the
        // generic downsampling workflow
        pairedReads
        | transpose
        | _downsample
        | set { downsampled }

        // We need to do some munging in order to arrive at the correct
        // output in a stable order. Specifically:
        // * Combine the sort key (FASTQ basename) and downsampled
        //   output into a map
        // * Pivot (i.e., un-transpose)
        // * Sort the pivoted reads by the sort key, which is ultimately
        //   disregarded leaving the appropriate downsampled output
        downsampled.output
        | map { meta, sortKey, downsampled -> [
            meta,
            [
                sortKey: sortKey,
                downsampled: downsampled
            ]
        ]}
        | groupTuple(size: 2)
        | map { meta, reads -> [
            meta,
            reads.sort { a, b -> a.sortKey <=> b.sortKey }
                 .collect { it.downsampled }
        ]}
        | set { samples }

        // The same munging is applied to the info.yaml outputs
        downsampled.info
        | map { meta, sortKey, info -> [
            meta,
            [
                sortKey: sortKey,
                infoYaml: info
            ]
        ]}
        | groupTuple(size: 2)
        | map { meta, info -> [
            meta,
            info.sort { a, b -> a.sortKey <=> b.sortKey }
                .collect { it.infoYaml }
        ]}
        | set { info }

    emit:
        // Channel of tuples, of the form:
        // * Metadata<Sample name>
        // * Tuple of downsampled reads (read 1 and 2, respectively)
        output = samples

        // Channel of tuples, of the form:
        // * Metadata<Sample name>
        // * Tuple of downsampling info (for read 1 and read 2, respectively)
        info = info
}
