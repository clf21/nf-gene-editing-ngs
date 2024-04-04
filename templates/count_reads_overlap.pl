#!/usr/bin/env perl

use strict;
use warnings;
use File::Basename qw(dirname);
use File::Spec;
use YAML::XS qw(LoadFile);

# Template Tags:
#   N)
#     Number of combinations to count
#     NOTE {1..N}.bam, {1..N}.bam.bai and {1..N}.yaml are assumed to
#          exist in the working directory
#   task.ext.samtools)
#     Samtools command definition
#
# Output
#   standard output)
#     Count of reads overlapping the region for each input (delimited)

my $N = !{N};

my @counts = ();
my $delimiter = "!{params._internal.delimiter}";

foreach my $i (1 .. $N) {
  my $sample_bam = "$i.bam";
  my $amplicon = LoadFile("$i.yaml");

  my $info = $amplicon->{info};
  my $amplicon_location = "$info->{chr}:$info->{start}-$info->{end}";

  my $amplicon_read_count;
  if ($info->{barcode}) {
    my $rc_barcode = reverse($info->{barcode});
    $rc_barcode =~ tr /atcgATCG/tagcTAGC/;
    my $viewcmd = !{Escape.cmdAsPerlString(task.ext.samtools, 'view', '$sample_bam', '$amplicon_location')};
    my $countcmd = "awk -F '\\t' '(\$10 ~ /^$info->{barcode}|$rc_barcode\$/) {i++} END{print i}'";
    $amplicon_read_count = `bash -c "set -o pipefail; $viewcmd | $countcmd"`;
    $? == 0 or die "ERROR: samtools barcode count failed: $?";
  }
  else {
    my $countcmd = !{Escape.cmdAsPerlString(task.ext.samtools, 'view', '-c', '$sample_bam', '$amplicon_location')};
    $amplicon_read_count = `$countcmd`;
    $? == 0 or die "ERROR: samtools count failed: $?";
  }
  chomp($amplicon_read_count);

  # Write count to stdout
  push @counts, ($amplicon_read_count + 0);
}

print join($delimiter, @counts);
