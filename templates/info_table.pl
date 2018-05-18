#!/usr/bin/env perl

use strict;
use warnings;
use File::Basename qw(dirname);
use File::Spec;
use YAML::XS qw(LoadFile DumpFile);
# use diagnostics;  # For debugging
# use Data::Dumper;  # For debugging

# Template tags:
#   params._doTrimming)
#     Trimming enabled/disabled
#   params.mergeMode)
#     Merge mode
#   task.ext.maxReads)
#     Read count threshold
#   infoYaml)
#     Info YAML file
#
# Output - Files stored in `$PWD`:
#   `table.txt`)
#     Formatted table of results

my $info = LoadFile("!{infoYaml}");

open(my $out, '>', 'table.txt')
  or die "Can't open output file 'table.txt': $!.";

my @columns =  ('Sample', 'Reads.Input');
push @columns, ('Reads.Downsampled', 'Percent.Downsampled') if !{task.ext.maxReads ? 1 : 0};
push @columns, ('Reads.Trimmed', 'Percent.Trimmed') if !{params._doTrimming ? 1 : 0};
push @columns, ('Read');
push @columns, ('Reads.After_merge', 'Percent.After_merge') if !{params.mergeMode == MergeMode.NoMerge ? 0 : 1};
push @columns, (
  'Reads.Aligned_once', 'Reads.Aligned_multiple', 'Reads.Aligned', 'Percent.Aligned',
  'Amplicon', 'Reads.Overlapping_amplicon', 'Percent.Overlapping_amplicon',
  'Reads.Genotyped', 'Percent.Genotyped'
);
print $out join("\t", @columns) . "\n";

foreach my $sample_name (sort keys %{ $info->{samples} }) {
  my %sample = %{ $info->{samples}{$sample_name} };
  my %row_output;
  $row_output{'Sample'} = $sample_name;

  # Reads 1 and 2 are downsampled in the same way.
  if (exists($sample{downsample_fastq})) {
    my %downsample = %{ $sample{downsample_fastq}{read_1} };
    $row_output{'Reads.Input'} = $downsample{reads_before_downsampling};
    $row_output{'Reads.Downsampled'} = $downsample{reads_after_downsampling};
    $row_output{'Percent.Downsampled'} = $row_output{'Reads.Input'} ? sprintf("%.2f", 100 * $row_output{'Reads.Downsampled'} / $row_output{'Reads.Input'}) : 'NA';
  }

  # If in unpaired mode, then trimming happens in each individual read.
  # If in paired mode, then trimming happens on the read pair.
  if (exists($sample{trim_single_reads}) or exists($sample{trim_paired_reads})) {
    my %trim = %{ exists($sample{trim_single_reads}) ? $sample{trim_single_reads} : $sample{trim_paired_reads} };
    $row_output{'Reads.Input'} = $trim{Input_reads} unless $row_output{'Reads.Input'};
    @row_output{('Reads.Trimmed', 'Percent.Trimmed')} = $trim{Surviving} =~ /(\d+) \(([\d.]+)%\)/;
  }

  foreach my $read_name (sort keys %{ $sample{reads} }) {
    my $read = $sample{reads}{$read_name};
    $row_output{'Read'} = $read_name;

    # If in paired mode, then merge information is attached to the merged read.
    if (exists($sample{merge_paired_reads}) and ($read_name eq 'Merged' or !{params.mergeMode == MergeMode.Auto ? 1 : 0})) {
      my %merge = %{ $sample{merge_paired_reads} };
      $row_output{'Reads.Input'} = $merge{Total_pairs} unless $row_output{'Reads.Input'};
      $row_output{'Reads.After_merge'} = $read_name eq "Merged" ? $merge{Combined_pairs} : $merge{Uncombined_pairs};
      $row_output{'Percent.After_merge'} = $merge{Total_pairs} ? sprintf("%.2f", 100 * $row_output{'Reads.After_merge'} / $merge{Total_pairs}) : 'NA';
    } else {
      $row_output{'Reads.After_merge'} = 'NA';
      $row_output{'Percent.After_merge'} = 'NA';
    }

    my %alignment = %{ $read->{align_reads_to_reference} };
    $row_output{'Reads.Input'} = $alignment{reads} unless $row_output{'Reads.Input'};
    ($row_output{'Reads.Aligned_once'}) = $alignment{aligned_exactly_1_time} =~ /^(\d+)/;
    ($row_output{'Reads.Aligned_multiple'}) = $alignment{aligned_more_than_1_times} =~ /^(\d+)/;
    $row_output{'Reads.Aligned'} = $row_output{'Reads.Aligned_once'} + $row_output{'Reads.Aligned_multiple'};
    $row_output{'Percent.Aligned'} = $alignment{overall_alignment_rate};
    $row_output{'Percent.Aligned'} =~ s/%//;

    foreach my $amplicon_name (sort keys %{ $read->{amplicons} }) {
      my $amplicon = $read->{amplicons}{$amplicon_name};
      $row_output{'Amplicon'} = $amplicon_name;
      $row_output{'Reads.Overlapping_amplicon'} = $amplicon->{count_reads_overlap}->{count};
      $row_output{'Percent.Overlapping_amplicon'} =
        $row_output{'Reads.Aligned'} ? sprintf("%.2f", 100 * $row_output{'Reads.Overlapping_amplicon'} / $row_output{'Reads.Aligned'}) : 'NA';
      if ($amplicon->{summarize_alleles}) {
        $row_output{'Reads.Genotyped'} = $amplicon->{summarize_alleles}->{'Total.Reads'};
        $row_output{'Percent.Genotyped'} =
          $row_output{'Reads.Overlapping_amplicon'} ? sprintf("%.2f", 100 * $row_output{'Reads.Genotyped'} / $row_output{'Reads.Overlapping_amplicon'}) : 'NA';
      }
      else {
        $row_output{'Reads.Genotyped'} = 'NA';
        $row_output{'Percent.Genotyped'} = 'NA';
      }

      foreach my $column (@columns) {
        print $out (defined($row_output{$column}) ? $row_output{$column} : 'NA') . "\t";
      }
      print $out "\n";
    }
    unless (%{ $read->{amplicons} }) {
      foreach my $column (@columns) {
        print $out (defined($row_output{$column}) ? $row_output{$column} : 'NA') . "\t";
      }
      print $out "\n";
    }

    # print Dumper(\%row_output);  # For debugging
  }
}
