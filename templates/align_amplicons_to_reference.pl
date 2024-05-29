#!/usr/bin/env perl

use strict;
use warnings;
use File::Basename qw(fileparse);
use IPC::Open2;
use YAML::XS qw(LoadFile DumpFile);
# use Data::Dumper;  # For debugging

# Template tags:
#   bt2IndexPath)
#     Bowtie2 reference/index path
#   params.bowtie2.prefix)
#     Reference file pattern/prefix
#   params.genome)
#     Reference genome ID
#   ampliconsYaml)
#     Amplicons YAML file
#   task.ext.bowtie2)
#     Bowtie2 command definition
#
# Output - Files stored in `$PWD`:
#   `alignments.yaml`)
#     Associates amplicon name with aligment

my $genome_base = "!{bt2IndexPath}/!{params.bowtie2.prefix}";
my $amplicons = LoadFile("!{ampliconsYaml}");

my @cmd = !{Escape.cmdAsPerlList(task.ext.bowtie2,
  '-x', '$genome_base',
  '--no-hd', /* Suppress SAM header lines (starting with @) */
  '-f',      /* Bowtie2 input will be in FASTA format */
  '-U', '-'  /* Bowtie2 input from stdin */
)};
my $pid = open2(my $out, my $in, @cmd);

foreach my $name (sort keys %{ $amplicons }) {
  print $in ">$name\n$amplicons->{$name}->{seq}\n";
}
close($in);

# Read SAM output from bowtie2
while (<$out>) {
  chomp;
  my @fields = split(/\s/);
  my $name = $fields[0];

  ${amplicons}->{$name}->{genome} = "!{params.genome}";

  ${amplicons}->{$name}->{chr} = $fields[2];
  ${amplicons}->{$name}->{start} = $fields[3] - 1; # Use BED, not SAM, coord format
  ${amplicons}->{$name}->{CIGAR} = $fields[5];

  # See discussion at https://github.com/pfizer-rd/nf-gene-editing-ngs/pull/54#discussion_r1530285785
  ${amplicons}->{$name}->{strand} = ($fields[1] % 32 >= 16 ? '-' : '+');
}

DumpFile('alignments.yaml', $amplicons);

waitpid($pid, 0);
$? == 0 or die "ERROR: bowtie2 failed: $?";
