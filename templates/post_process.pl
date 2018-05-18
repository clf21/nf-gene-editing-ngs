#!/usr/bin/env perl

use strict;
use warnings;
use IO::Compress::Gzip qw(gzip);

# TODO Injected inputs need to be escaped
my @sample_names = qw(!{sampleNames.join(" ")});
my @read_ids = qw(!{readIds.join(" ")});
my @amplicon_names = qw(!{ampliconNames.join(" ")});
my @result_dirs = qw(!{analysisResults.join(" ")});

# Combine and compress allele frequency tables from all results
# directories, prepending sample name, read ID and amplicon columns
my $combined_alleles_gz = new IO::Compress::Gzip("alleles_frequency_table.txt.gz")
  or die "Can't open output file 'alleles_frequency_table.txt.gz': $!.";

my $header_written = 0;

foreach my $i (0 .. $#sample_names) {
  my $sample_name = @sample_names[$i];
  my $read_id = @read_ids[$i];
  my $amplicon_name = @amplicon_names[$i];
  my $result_dir = @result_dirs[$i];

  open(my $af_table, '<', "${result_dir}/CRISPResso_output/Alleles_frequency_table.txt");

  # Write header
  my $header = <$af_table>;
  unless ($header_written) {
    print $combined_alleles_gz "Sample\tRead\tAmplicon\t${header}";
    $header_written = 1;
  }

  # Write rows
  while (<$af_table>) {
    print $combined_alleles_gz "${sample_name}\t${read_id}\t${amplicon_name}\t$_";
  }

  close($af_table);
}

close($combined_alleles_gz);
