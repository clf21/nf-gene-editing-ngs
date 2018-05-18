process _summaryTableYaml {
    publishDir "${params.outdir}", mode: "copy", overwrite: true

    input:
        // Tuple of analysis identifiers (sample name, read ID, amplicon
        // name), output type (results, skipped or failed) and respective
        // summary YAML files
        //
        // NOTE Nextflow will rename each of the input files to N.yaml,
        // where N is an increasing integer starting from 1
        tuple val(metas), val(outputTypes), path(analysisSummariesYaml, name: "*.yaml")

    output:
        path "summary.yaml"

    shell:
        // If there is only one analysis, then analysisSummariesYaml won't be
        // an array. This code corrects that for downstream assumptions.
        // NOTE From NF 23.09, we can set the path arity to achieve this
        if (metas.size() == 1) { analysisSummariesYaml = [ analysisSummariesYaml ] }

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
        assert ampliconNames.size() == outputTypes.size()
        assert outputTypes.size() == analysisSummariesYaml.size()

        '''
        #!/usr/bin/env perl

        use strict;
        use warnings;
        use YAML::XS qw(LoadFile DumpFile);

        # TODO Injected inputs need to be escaped
        my @sample_names = qw(!{sampleNames.join(" ")});
        my @read_ids = qw(!{readIds.join(" ")});
        my @amplicon_names = qw(!{ampliconNames.join(" ")});
        my @headings = qw(!{outputTypes.join(" ")});
        my @summary_yamls = qw(!{analysisSummariesYaml.join(" ")});

        DumpFile("summary.yaml", [ map {
            {
                sample_name => $sample_names[$_],
                sample_read => $read_ids[$_],
                amplicon_name => $amplicon_names[$_],
                $headings[$_] => LoadFile($summary_yamls[$_])
            }
        } (0 .. $#sample_names) ]);
        '''
}

// Nextflow wrapper to summary_table.pl
process _summaryTable {
    publishDir "${params.outdir}", mode: "copy", overwrite: true

    input:
        // Summary YAML Path
        // With the following structure:
        // * List of:
        //   * .sample_name                        String
        //   * .sample_read                        String
        //   * .amplicon_name                      String
        //   * One of the following:
        //     1. .skipped[].insufficient_overlap  Integer
        //     2. .failed[]                        String (placeholder)
        //     3. .results
        //        * ."Total.Reads"                 String
        //        * .Genotype                      String
        //        * ."Num.Reads_${CLASS}"          String (optional)
        //        * ."Percent.Reads_${CLASS}"      String (optional)
        //
        // ${CLASS} values defined in summary_table.pl:L26-28
        path summaryYaml

    output:
        // Formatted table of results
        path "summary.txt"

    script:
        """
        summary_table.pl "$summaryYaml"
        """
}

workflow generateSummaryTable {
    take:
        analysisSummaries

    main:
        analysisSummaries
        | collect(flat: false, sort: { a, b -> a[0] <=> b[0] }) // Sort on metadata for stable output
        | map { it.transpose() }
        | _summaryTableYaml
        | _summaryTable
}
