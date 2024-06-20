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

sub is_numeric {
  my $candidate = shift;
  return 0 unless defined $candidate;
  return $candidate =~ /^-?\d+\.?\d*$/ || $candidate =~ /^-?\.\d+$/;
}

sub common_keys {
  my ($lhs, $rhs) = @_;

  my %count;
  $count{$_}++ for keys %$lhs, keys %$rhs;

  return sort grep { $count{$_} > 1 } keys %count;
}

sub homocmp {
  my ($lhs, $rhs) = @_;

  if (ref $lhs eq 'HASH' && ref $rhs eq 'HASH') {
    # Compare hashes by common keys (lexicographically ordered)
    foreach my $key (common_keys($lhs, $rhs)) {
      my $diff = homocmp($lhs->{$key}, $rhs->{$key});
      return $diff if $diff;
    }

    return 0;

  } elsif (ref $lhs eq 'ARRAY' && ref $rhs eq 'ARRAY') {
    # Compare arrays element-wise, then by length
    my $lhs_length = @$lhs;
    my $rhs_length = @$rhs;
    my $min_length = $lhs_length <= $rhs_length ? $lhs_length : $rhs_length;

    for my $i (0 .. $min_length - 1) {
      my $diff = homocmp($lhs->[$i], $rhs->[$i]);
      return $diff if $diff;
    }

    return $lhs_length <=> $rhs_length;

  } elsif (is_numeric($lhs) && is_numeric($rhs)) {
    # Numeric comparison
    return $lhs <=> $rhs;

  } elsif (defined $lhs && defined $rhs) {
    # Lexicographic comparison
    return $lhs cmp $rhs;
  }

  # Handle undefined values
  return defined $lhs ? 1 : (defined $rhs ? -1 : 0);
}

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
    # Concatenate arrays, dedup and sort
    my %observed;
    return [ sort { homocmp($a, $b) } grep { !$observed{$_}++ } (@$lhs, @$rhs) ];

  } else {
    # Otherwise, scalar RHS wins
    return $rhs;
  }
}

sub merge_docs {
  my $output = shift;

  while (my $next_doc = shift) {
    $output = merge($output, $next_doc);
  }
  return $output;
}

my $output = merge_docs(LoadFile(shift));

while (my $next = shift) {
  $output = merge_docs($output, LoadFile($next));
}

DumpFile("merged.yaml", $output);
