#!/usr/bin/env perl

use strict;
use warnings;
use File::Copy qw(copy move);
use YAML::XS qw(LoadFile DumpFile);

# Prepare experiment metadata
my $experiment_file = '!{experimentMetadata}';
if ($experiment_file eq '!{params._internal.noFile}') {
  $experiment_file = 'experiment.yaml';
  DumpFile($experiment_file, { experiment => {} });
}

# Merge sample and experiment metadata to metadata.yaml
system("merge_yaml.pl", "!{sampleMetadata}", $experiment_file);
move("merged.yaml", "metadata.yaml");

# Publish serialised parameters to params.yaml
open(my $params_file, '>', 'params.yaml');

print $params_file <<'YAML';
!{paramsYaml}
YAML

close($params_file);

# Prepare published amplicons to amplicons.yaml
copy("!{preparedAmplicons}", "amplicons.yaml");
