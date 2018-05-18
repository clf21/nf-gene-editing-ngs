#!/usr/bin/env perl

use strict;
use warnings;
use YAML::XS qw(DumpFile);
# use Data::Dumper;  # For debugging

# Template tags:
#   sampleFastqPair)
#     Array of sample FASTQ reads (read 1 and read 2, respectively)
#   task.ext.trimmomatic)
#     Trimmomatic command definition
#   ?task.ext.trimmomatic.adapterPath)
#     Path to adapter trimming FASTA file
#   ?task.ext.trimmomatic.headcrop)
#     Headcrop
#
# Output - Files stored in `$PWD`:
#   `read1_trimmed.fastq.gz`)
#     Trimmed read1
#   `read2_trimmed.fastq.gz`)
#     Trimmed read2
#   `info.yaml`)
#     Additional details about read trimming

my @trimcmd = !{Escape.cmdAsPerlList(task.ext.trimmomatic,
  'PE',
  '-phred33',
  sampleFastqPair[0],
  sampleFastqPair[1],
  'read1_trimmed.fastq.gz',
  'read1_unpaired.fastq.gz',
  'read2_trimmed.fastq.gz',
  'read2_unpaired.fastq.gz',
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
      $key =~ s/( Pair)|(Both )//; # Match the keys for single read info
      $key =~ s/ /_/g;
      $value =~ s/ $//;
      $info{$key} = $value;
    }
  }
}
$info{'Trim_Type'} = "Paired";

DumpFile('info.yaml', \%info);
