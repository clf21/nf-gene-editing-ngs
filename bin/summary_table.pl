#!/usr/bin/env perl

use strict;
use warnings;
use YAML::XS qw(LoadFile);
# use Data::Dumper;  # For debugging

# Input - Positional arguments:
#   0) Summary YAML file
#     sample_name)
#     ?sample_read)
#     amplicon_name)
#     results)
# Output - Files stored in `$PWD`:
#   `summary.txt`)
#     Formatted table of results

my $summary = LoadFile($ARGV[0])
  or die "Invalid summary file '$ARGV[0]'.";

open(my $out, '>', 'summary.txt')
  or die "Can't open output file 'summary.txt': $!.";

my @classes = (
  'Unmodified', 'NHEJ', 'Mixed HDR-NHEJ', 'HDR', 'Frameshift', 'In-frame', 'Noncoding'
);
print $out "Sample\tRead\tAmplicon\tTotal.Reads\t";
foreach my $class (@classes) {
  print $out "Num.Reads_$class\tPercent.Reads_$class\t";
}
print $out "Genotype\n";

foreach my $row (@{ $summary }) {
  next if $row->{skipped};
  next if $row->{failed};
  my $read = $row->{sample_read} // 'Merged';
  print $out "$row->{sample_name}\t$read\t$row->{amplicon_name}\t";
  print $out $row->{results}->{'Total.Reads'} . "\t";
  foreach my $class (@classes) {
    if (defined($row->{results}->{"Num.Reads_$class"})) {
      print $out $row->{results}->{"Num.Reads_$class"} . "\t";
      print $out $row->{results}->{"Percent.Reads_$class"} . "\t";
    }
    else {
      print $out "NA\tNA\t";
    }
  }
  print $out $row->{results}->{'Genotype'} . "\n";
}
