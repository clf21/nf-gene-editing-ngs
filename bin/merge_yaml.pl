#!/usr/bin/env perl

use strict;
use warnings;
use YAML::XS qw(LoadFile DumpFile);

# Input - Positional arguments:
#   *) Input YAML files (at least one)
#
# Output - Files stored in `$PWD`:
#   `merged.yaml`)
#     Merged YAML output

sub merge {
  my ($lhs, $rhs) = @_;

  if (ref($lhs) eq 'HASH' && ref($rhs) eq 'HASH') {
    # Merge hashes
    my %merged = (%$lhs, %$rhs);

    for my $key (keys %merged) {
      if (exists $lhs->{$key} && exists $rhs->{$key}) {
        $merged{$key} = merge($lhs->{$key}, $rhs->{$key});
      }
    }

    return \%merged;

  } elsif (ref($lhs) eq 'ARRAY' && ref($rhs) eq 'ARRAY') {
    # Concatenate arrays
    return [@$lhs, @$rhs];

  } else {
    # Otherwise, scalar RHS wins
    return $rhs;
  }
}

my $output = LoadFile(shift);

while (my $next = shift) {
  $output = merge($output, LoadFile($next));
}

DumpFile("merged.yaml", $output);
