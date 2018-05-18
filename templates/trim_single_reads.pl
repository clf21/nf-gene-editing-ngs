#!/usr/bin/env perl

use strict;
use warnings;
use YAML::XS qw(DumpFile);
# use Data::Dumper;  # For debugging

# Input - Positional arguments:
# Template tags:
#   sampleFastq)
#     Sample FASTQ file
#   task.ext.trimmomatic)
#     Trimmomatic command definition
#   ?task.ext.trimmomatic.adapterPath)
#     Path to adapter trimming FASTA file
#   ?task.ext.trimmomatic.headcrop)
#     Headcrop
#
# Output - Files stored in `$PWD`:
#   `read_trimmed.fastq.gz`)
#     Trimmed reads
#   `info.yaml`)
#     Additional details about read trimming

my @trimcmd = !{Escape.cmdAsPerlList(task.ext.trimmomatic,
  'SE',
  '-phred33',
  sampleFastq,
  'read_trimmed.fastq.gz',
  task.ext.trimmomatic.adapterPath ? "ILLUMINACLIP:" + task.ext.trimmomatic.adapterPath + ":0:90:10:0:true" : Escape.noQuote("()"),
  task.ext.trimmomatic.headcrop ? "HEADCROP:" + task.ext.trimmomatic.headcrop : Escape.noQuote("()")
)};

# print Dumper(\@trimcmd);  # For debugging

my @cmd_stdout = `@trimcmd 2>&1`;
print STDOUT @cmd_stdout;
$? == 0 or die "ERROR: Trimmomatic failed: $?";

my %info;
foreach (@cmd_stdout) {
  if (my %trim_stats = /([a-zA-Z ]+): ([\d().% ]+)/g) {
    while (my ($key, $value) = each %trim_stats) {
      $key =~ s/ /_/g;
      $value =~ s/ $//;
      $info{$key} = $value;
    }
  }
}
$info{'Trim_Type'} = "Single";

DumpFile('info.yaml', \%info);
