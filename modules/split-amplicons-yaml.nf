/*
 * Split the combined amplicon specifications into individual YAML files
 * for each amplicon, with the following structure:
 *
 *   name    Amplicon name (formerly the combined YAML keys)
 *   info.*  Amplicon details (formerly the values under each YAML key)
 *
 * The `info` fields are simply passed through from the source; there is
 * no expectation as to what they should look like.
 *
 */

process _splitAmpliconsYaml {
    input:
        path ampliconsYaml

    output:
        path "*.yaml"

    shell:
        '''
        #!/usr/bin/env perl

        use strict;
        use warnings;
        use YAML::XS qw(LoadFile DumpFile);

        my $amplicons = LoadFile("!{ampliconsYaml}")
          or die "Invalid amplicons file '!{ampliconsYaml}'.";

        foreach my $name (keys %{ $amplicons }) {
          DumpFile("${name}.yaml", {
            name => $name,
            info => $amplicons->{$name}
          });
        }
        '''
}

workflow splitAmpliconsYaml {
    take:
        // Prepared, Combined Amplicons YAML Path
        ampliconsYaml

    main:
        ampliconsYaml
        | _splitAmpliconsYaml
        | flatten
        | set { splitAmplicons }

    emit:
        splitAmplicons
}
