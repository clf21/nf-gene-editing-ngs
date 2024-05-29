#!/usr/bin/env perl

use strict;
use warnings;
use YAML::XS qw(LoadFile DumpFile);

# Input - Positional arguments:
#   0) Amplicons YAML file
# Output - Files stored in `$PWD`:
#   `normalized.yaml`)
#     Normalized amplicons

my $amplicons = LoadFile($ARGV[0])
  or die "Invalid amplicon file '$ARGV[0]'.";

# Iterate through each named amplicon
foreach my $name (keys %$amplicons) {
  my $amplicon = $amplicons->{$name};
  my ($has_seq, $has_guide) = (0, 0);

  # Iterate through amplicon's keys
  foreach my $key (keys %$amplicon) {

    # Allow the HDR key to be case-insensitive
    if ($key ne "HDR" && $key =~ /^hdr$/i ) {
      $amplicon->{HDR} = delete $amplicon->{$key};
      $key = "HDR";
    }

    # Capitalise the seq value
    if ($key eq "seq") {
      $has_seq = 1;
      $amplicon->{$key} = uc $amplicon->{$key};
    }

    # Normalise the guide value to a list of dictionaries; capitalising
    # each value
    if ($key eq "guide") {
      my $invalid_guide = 0;

      # If we don't have an array, then we presume to have a string,
      # which we split (by comma) into an array
      if (ref $amplicon->{$key} ne 'ARRAY') {
        $amplicon->{$key} = [ split /,/, $amplicon->{$key} ];
      }

      # Iterate through each guide
      foreach my $guide (@{ $amplicon->{$key} }) {
        if (ref $guide eq 'HASH') {
          # Check the hash contains the `seq` key and capitalise
          if (exists ${guide}->{seq}) {
            ${guide}->{seq} = uc ${guide}->{seq};
          } else {
            $invalid_guide = 1;
          }
        } else {
          # Otherwise normalise the capitalised sequence into a hash
          $guide = { seq => uc $guide };
        }
      }

      $has_guide = 1 unless $invalid_guide;
    }

    # Normalise the HDR and coding values to lists, if they're not
    # already; capitalising each value
    if ($key eq "HDR" || $key eq "coding") {
      my $values = ref $amplicon->{$key} eq "ARRAY"
        ? $amplicon->{$key}
        : [ split /,/, $amplicon->{$key} ];

      $amplicon->{$key} = [ map { uc } @$values ];
    }
  }

  die "Amplicon '${name}' has no expected sequence!" unless $has_seq;
  die "Amplicon '${name}' has no valid guide sequence(s)!" unless $has_guide;
}

DumpFile("normalized.yaml", $amplicons);
