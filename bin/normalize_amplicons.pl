#!/usr/bin/env perl

use strict;
use warnings;
use YAML::XS qw(LoadFile DumpFile);
# use Data::Dumper;  # For debugging

# Input - Positional arguments:
#   0) Amplicons YAML file
# Output - Files stored in `$PWD`:
#   `amplicons.yaml`)
#     Normalized amplicons

my $amplicons = LoadFile($ARGV[0])
  or die "Invalid amplicon file '$ARGV[0]'.";

foreach my $name (keys %{ $amplicons }) {
  foreach my $value (keys %{ $amplicons->{$name} }) {
    $amplicons->{$name}->{$value} = uc $amplicons->{$name}->{$value};
  }
}

DumpFile("$ARGV[0].normalized", $amplicons);
