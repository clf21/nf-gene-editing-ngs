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
        tuple stdout, path("*.yaml")

    shell:
        '''
        #!/usr/bin/env perl

        use strict;
        use warnings;
        use YAML::XS qw(LoadFile DumpFile);

        my $amplicons = LoadFile("!{ampliconsYaml}")
          or die "Invalid amplicons file '!{ampliconsYaml}'.";

        my $index = 0;
        my @names = keys %{ $amplicons };

        foreach my $name (@names) {
          # Name each amplicon YAML file with a numerical index in the
          # same order in which they occur in @names; this will preserve
          # the correlation with the names output on stdout, which are
          # then matched up downstream.
          # NOTE We assume that we'll never have more than 10^8 amplicons!
          my $padded_index = sprintf("%08d", $index);

          DumpFile("${padded_index}.yaml", {
            name => $name,
            info => $amplicons->{$name}
          });

          $index++;
        }

        print join("!{params._internal.delimiter}", @names);
        '''
}

workflow splitAmpliconsYaml {
    take:
        // Prepared, Combined Amplicons YAML Path
        ampliconsYaml

    main:
        ampliconsYaml
        | _splitAmpliconsYaml
        | flatMap { names, ampliconYamls ->
            // `names` is a delimited string of amplicon names, in the
            // same order as the `ampliconYamls` list. We split these
            // and transpose them together.
            names = names.tokenize(params._internal.delimiter)

            [ names, ampliconYamls ].transpose()
        }
        | set { splitAmplicons }

    emit:
        // Channel of tuples, of the form:
        // * Amplicon name (extracted from YAML file)
        // * Amplicon YAML file
        splitAmplicons
}
