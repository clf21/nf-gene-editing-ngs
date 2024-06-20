/* Split the sample and experiment metadata YAML file into:
 *
 * - One YAML file per sample
 * - The experiment metadata YAML
 *
 * This is so the sample metadata can be pushed downstream, to be
 * recombined later.
 */

process _splitMetadataYaml {
    input:
        path metadataYaml

    output:
        path "samples/*.yaml", emit: samples
        path "experiment.yaml", emit: experiment

    shell:
        '''
        #!/usr/bin/env perl

        use strict;
        use warnings;
        use YAML::XS qw(LoadFile DumpFile);

        my $metadata = LoadFile("!{metadataYaml}")
          or die "Invalid metadata file '!{metadataYaml}'.";

        die("Invalid metadata file '!{metadataYaml}'.")
          unless exists($metadata->{samples}) && exists($metadata->{experiment});

        mkdir("samples");

        foreach my $name (keys %{ $metadata->{samples} }) {
          DumpFile("samples/${name}.yaml", {
            name => $name,
            metadata => $metadata->{samples}->{$name}
          });
        }

        DumpFile("experiment.yaml", {
            experiment => $metadata->{experiment}
        });
        '''
}

workflow acquireMetadata {
    take:
        // Sample and experiment metadata YAML path (as a string)
        metadataYaml

    main:
        if (metadataYaml) {
            _splitMetadataYaml(file(metadataYaml))
            | set { output }

            output.samples
            | flatten
            | map { sampleYaml -> new Metadata(sampleYaml) }
            | set { samples }

            output.experiment
            | set { experiment }

        } else {
            samples = Channel.empty()
            experiment = file(params._internal.noFile, checkIfExists: false)
        }

    emit:
        samples = samples
        experiment = experiment
}
