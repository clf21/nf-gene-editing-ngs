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

sub rc {
  # Reverse complement a DNA sequence
  my $seq = shift;
  $seq =~ tr/acgtACGT/tgcaTGCA/;
  return reverse $seq;
}

sub invert {
  # Invert a strand direction (i.e., "+" -> "-" and vice versa)
  my $strand = shift;
  $strand =~ tr/+-/-+/;
  return $strand;
}

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

  # Amplicon alignment end position (BED format)
  $alignments->{$name}->{end} = $alignments->{$name}->{start} + $alignment_length;

  # Amplicon sequence has coords (x, y), in BED standard. That is, a
  # 0-based coordinate system where the start base is included, but the
  # end base is not. For example, (0, 100) will be a sequence 100 bases
  # in length, from 0 to 99 inclusive.
  my ($x, $y) = ($alignments->{$name}->{start}, $alignments->{$name}->{end});
  my $fwd_seq = ($alignments->{$name}->{strand} eq '+');

  # Compute each guide sequence's coordinates (BED format) and orientation
  # See Issue #126 for details on this algorithm
  foreach my $guide (@{ $alignments->{$name}->{guide} }) {
    # Locate the guide in the amplicon sequence
    my $i = index($alignments->{$name}->{seq}, $guide->{seq});
    my $guide_strand = '+';

    if ($i == -1) {
      # Try the reverse complement, if not found in the forwards direction
      $i = index($alignments->{$name}->{seq}, rc($guide->{seq}));
      $guide_strand = '-';
    }

    if ($i == -1) {
      # If it's still not found, then there's nothing we can do about it
      $guide->{failed} = 'Guide not found in amplicon';

    } else {
      # Guide coordinates relative to the amplicon
      my %coords_wrt_amplicon = (
        start  => $i,
        end    => $i + (length $guide->{seq}),
        strand => $guide_strand
      );

      # Guide coordinates relative to the reference
      my %coords_wrt_reference;

      if ($fwd_seq) {
        # Amplicon in forward orientation, so the guide reference
        # location is its location in the amplicon plus the amplicon
        # offset
        %coords_wrt_reference = (
          start  => $x + $coords_wrt_amplicon{start},
          end    => $x + $coords_wrt_amplicon{end},
          strand => $coords_wrt_amplicon{strand}
        );

      } else {
        # Amplicon in reverse orientation, so start at the amplicon end
        # and count backwards to find the guide reference location,
        # inverting the strand
        %coords_wrt_reference = (
          start  => $y - $coords_wrt_amplicon{end},
          end    => $y - $coords_wrt_amplicon{start},
          strand => invert($coords_wrt_amplicon{strand})
        );
      }

      $guide->{coords} = {
        amplicon  => \%coords_wrt_amplicon,
        reference => \%coords_wrt_reference
      };
    }
  }
}

DumpFile('coordinates.yaml', $alignments);
