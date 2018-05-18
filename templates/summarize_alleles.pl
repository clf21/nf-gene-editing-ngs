#!/usr/bin/env perl

use strict;
use warnings;
use File::Basename qw(dirname);
use File::Spec;
use YAML::XS qw(LoadFile DumpFile);
# use Data::Dumper;  # For debugging


# Template tags:
#   analysisDir)
#     Allele analysis output directory
#   task.ext.summary.frameshiftThreshold)
#     Percentage of frameshift reads necessary to call a Knockout genotype.
#   task.ext.summary.hdrThreshold)
#     Percentage of HDR reads necessary to call an HDR genotype.
#   task.ext.summary.unmodifiedThreshold)
#     Percentage of unmodified reads necessary to call a Wildtype genotype.
#   task.ext.summary.wtFrameshiftMax)
#     Maximum number of frameshift reads that can be present and still call a Wildtype genotype.
#
# Output - Files stored in `$PWD`:
#   `info.yaml`)
#     Summary of CRISPResso results

my $alleles = "!{analysisDir}";

my $editing_quant_file = "$alleles/CRISPResso_output/Quantification_of_editing_frequency.txt";
my $frameshift_quant_file = "$alleles/CRISPResso_output/Frameshift_analysis.txt";

# Store results in hash
my %summary;

# Open editing quantification file
my $editing_quant_fh;
open($editing_quant_fh, $editing_quant_file)
  or die "ERROR: Could not read CRISPResso quantification summary file '$editing_quant_file'.";

# Find read counts for each edit class
my $total_reads = 0;
while (<$editing_quant_fh>) {
  next unless /- ([-a-zA-Z ]+):(\d+) reads/;
  $summary{$1} = $2;
  $total_reads += $2;
}
$summary{'Total.Reads'} = $total_reads;

# Open frameshift quantification file if present and get read counts for each frameshift class
my $frameshift_quant_fh;
if (open($frameshift_quant_fh, $frameshift_quant_file)) {
  while (<$frameshift_quant_fh>) {
    next unless /([-a-zA-Z]+) mutation:(\d+) reads/;
    $summary{$1} = $2;
  }
}

# Calculate percent of total reads for each class, except for total,
# and distinguish read counts from read percentages in an easily parsed way
foreach my $class (keys(%summary)) {
  next if $class eq 'Total.Reads';
  $summary{"Percent.Reads_$class"} = sprintf("%.2f", $summary{$class} / $total_reads * 100);
  $summary{"Num.Reads_$class"} = delete($summary{$class});
}

# Define genotype simply based on percent of frameshift, HDR, and unmodified reads
my $unmodified = $summary{'Percent.Reads_Unmodified'};
my $frameshift = $summary{'Percent.Reads_Frameshift'};
my $hdr = $summary{'Percent.Reads_HDR'};
$summary{'Genotype'} = 'Other';
$summary{'Genotype'} = 'Knockout' if $frameshift && $frameshift > !{task.ext.summary.frameshiftThreshold};
$summary{'Genotype'} = 'HDR' if $hdr && $hdr > !{task.ext.summary.hdrThreshold};
$summary{'Genotype'} = 'Wild-type' if $unmodified > !{task.ext.summary.unmodifiedThreshold} &&
                                      (! $frameshift || $frameshift < !{task.ext.summary.wtFrameshiftMax});

DumpFile('info.yaml', \%summary);
