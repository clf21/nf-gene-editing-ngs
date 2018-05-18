#!/usr/bin/env perl

use strict;
use warnings;
use YAML::XS qw(DumpFile);

# Template Tags:
#   bt2IndexPath)
#     Bowtie2 reference/index path
#   meta.sampleName)
#     Sample name
#   sampleFastq)
#     Sample FASTQ filename
#   params.bowtie2.pattern)
#     Reference file pattern/prefix
#   task.ext.bowtie2)
#     Bowtie2 command definition
#   task.ext.samtools)
#     Samtools command definition
#
# Output - Files stored in `$PWD`:
#   `aligned.bam` and `aligned.bam.bai`)
#     Output of samtools
#   `info.yaml`)
#     Additional details about read alignment

my $genome_base = "!{bt2IndexPath}/!{params.bowtie2.pattern}";
my $sample_name = "!{meta.sampleName}";
my $fastq = "!{sampleFastq}";

my @bowtie2_cmd = !{Escape.cmdAsPerlList(task.ext.bowtie2,
  '-U', '$fastq',
  '-x', '$genome_base',
  '--rg-id', '$sample_name',
  '--rg', 'SM:${sample_name}'
)};

my @samtools_cmd = !{Escape.cmdAsPerlList(task.ext.samtools,
  'sort',
  '-o', 'aligned.bam',
  '-'
)};

my $bt2_summary_file = 'bowtie2_summary.txt';

system('bash', '-c', "set -o pipefail; @bowtie2_cmd 2>$bt2_summary_file | @samtools_cmd") == 0
  or die "ERROR: bowtie2 samtools pipe failed: $?";

system(!{Escape.cmdAsPerlString(task.ext.samtools, 'index', 'aligned.bam')}) == 0
  or die "ERROR: samtools indexing failed: $?";

open(my $bt2_summary, $bt2_summary_file)
  or die "ERROR: bowtie2 summary file not found";

my %info;
while (<$bt2_summary>) {
  print STDERR $_;
  if (my ($count, $description) = /\s*([\d().% ]+) ([\w> ]+)/) {
    next if $description eq 'were unpaired';
    $description =~ s/>/more than /;
    $description =~ s/ /_/g;
    $info{$description} = $count;
  }
}

DumpFile('info.yaml', \%info);
