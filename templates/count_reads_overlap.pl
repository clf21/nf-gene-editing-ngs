#!/usr/bin/env perl

use strict;
use warnings;
use File::Basename qw(dirname);
use File::Spec;
use YAML::XS qw(LoadFile);

# Template Tags:
#   ampliconYaml)
#     Amplicon YAML file
#       name)
#         name of the amplicon
#       info)
#         amplicon alignment coordinates
#   sampleBam)
#     BAMfile of aligned sample read
#   task.ext.samtools)
#     Samtools command definition
#
# Output
#   standard output)
#     Count of reads overlapping the region

my $amplicon = LoadFile("!{ampliconYaml}");

my $info = $amplicon->{info};
my $amplicon_location = "$info->{chr}:$info->{start}-$info->{end}";

my $amplicon_read_count;
if ($info->{barcode}) {
  my $rc_barcode = reverse($info->{barcode});
  $rc_barcode =~ tr /atcgATCG/tagcTAGC/;
  my $viewcmd = !{Escape.cmdAsPerlString(task.ext.samtools, 'view', sampleBam, '$amplicon_location')};
  my $countcmd = "awk -F '\\t' '(\$10 ~ /^$info->{barcode}|$rc_barcode\$/) {i++} END{print i}'";
  $amplicon_read_count = `bash -c "set -o pipefail; $viewcmd | $countcmd"`;
  $? == 0 or die "ERROR: samtools barcode count failed: $?";
}
else {
  my $countcmd = !{Escape.cmdAsPerlString(task.ext.samtools, 'view', '-c', sampleBam, '$amplicon_location')};
  $amplicon_read_count = `$countcmd`;
  $? == 0 or die "ERROR: samtools count failed: $?";
}
chomp($amplicon_read_count);

# Write count to stdout
$amplicon_read_count += 0;
print "${amplicon_read_count}";
