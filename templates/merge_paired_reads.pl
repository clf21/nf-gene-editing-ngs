#!/usr/bin/env perl

use strict;
use warnings;
use YAML::XS qw(LoadFile DumpFile);
# use Data::Dumper;  # For debugging

# Template tags:
#   sampleFastqPair)
#     Array of sample FASTQ reads (read 1 and read 2, respectively)
#   task.ext.flash)
#     FLASH command definition
#   task.ext.flash.minOverlap)
#     FLASH's --min-overlap argument
#   task.ext.flash.maxOverlap)
#     FLASH's --max-overlap argument
#
# Output - Files stored in `$PWD`:
#   `out.extendedFrags.fastq.gz`)
#     FLASH output file
#   `info.yaml`)
#     Additional details about read merging

my @cmd = !{Escape.cmdAsPerlList(task.ext.flash,
  sampleFastqPair[0],
  sampleFastqPair[1],
  '--output-dir', '.',
  '--min-overlap', task.ext.flash.minOverlap,
  '--max-overlap', task.ext.flash.maxOverlap,
  '--allow-outies',
  '--compress'
)};

my @cmd_stdout = `@cmd`;
print STDOUT @cmd_stdout;
$? == 0 or die "ERROR: FLASH failed: $?";

my %info;
while (my $line = shift(@cmd_stdout)) {
  last if $line =~ /Read combination statistics/;
}
while (my $line = shift(@cmd_stdout)) {
  last unless (my ($key, $value) = ($line =~ /\[FLASH\]\s+([^:]+):\s+(.*)/));
  $key =~ s/ /_/;
  $info{$key} = $value;
}

DumpFile('info.yaml', \%info);
