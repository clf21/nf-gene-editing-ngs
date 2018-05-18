#!/usr/bin/env perl

use strict;
use warnings;
use IPC::Open2;
use YAML::XS qw(LoadFile DumpFile);

# Input - Positional arguments:
#   0) Alignments YAML file
# Output - Files stored in `$PWD`:
#   `coordinates.yaml`)
#     Associates amplicon name with aligment coordinates

my $alignments = LoadFile($ARGV[0])
  or die "Invalid alignments file '$ARGV[0]'.";

# Determine alignment start and end coordinates for each amplicon
foreach my $name (sort keys %{ $alignments }) {
  # Break CIGAR string into operations
  my @cigar_ops = $alignments->{$name}->{CIGAR} =~ /\d+[MIDNSHP]/g;

  # Loop through ops to determine start and end of alignment on reference sequence
  my $alignment_length = 0;
  foreach my $op (@cigar_ops) {
    my $op_length = substr($op, 0, -1);

    # Soft clipping
    if (substr($op, -1) eq "S") {
      # Determine whether this is clipping at the start or end,
      # and adjust start position and length appropriately
      if ($alignment_length == 0) {
        # Soft clipping at the start, move start position back
        $alignments->{$name}->{start} -= $op_length;
      }
      else {
        # Soft clipping at the end, increase length
        $alignment_length += $op_length;
      }
    }
    # Anything besides insertions or hard-clipping increases the length
    # along the reference (I and H do not affect reference positions)
    elsif (substr($op, -1) ne "I" || substr($op, -1) ne "H") {
      $alignment_length += $op_length;
    }
  }

  # Amplicon alignment end position
  $alignments->{$name}->{end} = $alignments->{$name}->{start} + $alignment_length - 1;
}

DumpFile('coordinates.yaml', $alignments);
