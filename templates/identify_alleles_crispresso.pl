#!/usr/bin/env perl

use strict;
use warnings;
use File::Basename qw(dirname);
use File::Spec;
use YAML::XS qw(LoadFile DumpFile);
# use Data::Dumper;  # For debugging

# Template Tags:
#   meta.sampleName)
#   meta.readId)
#   sampleBam)
#     BAM of aligned sample read
#   ampliconYaml)
#     name)
#       name of the amplicon
#     info)
#       amplicon input information and alignment coordinates
#   overlapCount)
#     Amplicon read overlap count
#   task.ext.maxReadsPerAmplicon)
#     Maximum amplicon read overlap count
#   task.ext.crispresso)
#     CRISPResso command definition
#   task.ext.crispresso.window)
#     Window (bp) around sgRNA
#   task.ext.samtools)
#     Samtools command definition
#
# Output - Files stored in `$PWD`:
#   `alleles_analysis`)
#     Output directory of allele analysis program (currently CRISPResso)

my $amplicon = LoadFile("!{ampliconYaml}");

my $info = $amplicon->{info};
my $amplicon_location = "$info->{chr}:$info->{start}-$info->{end}";

my $output_dir = "alleles_analysis";

my $reads_bam = 'reads.bam';
my $reads_fastq = 'reads.fastq';

# If a maximum number of reads per amplicon was set (ie, > 0),
# then calculate the fraction of reads to extract from the BAM file for this amplicon.
# Otherwise, all reads will be used when running the samtools command.
my $fraction_reads = 0;
if (!{task.ext.maxReadsPerAmplicon} < !{overlapCount}) {
  $fraction_reads = sprintf("%.3f", !{task.ext.maxReadsPerAmplicon} / !{overlapCount});
  print "Down-sampling amplicon to ~!{task.ext.maxReadsPerAmplicon} reads prior to analysis...\n";
}

# Use samtools to create a BAM file with reads that overlap this amplicon
if ($info->{barcode}) {
  # If this amplicon is associated with a barcode, filter out the reads matching the barcode from the input bam file
  my $rc_barcode = reverse($info->{barcode});
  $rc_barcode =~ tr /atcgATCG/tagcTAGC/;

  # Use samtools and awk to select reads overlapping the amplicon and matching the barcode
  my $viewcmd = "samtools view -h '!{sampleBam}' '$amplicon_location'";
  my $bcfiltercmd = "awk -F '\\t' '(\$1 ~ /^@/ || \$10 ~ /^$info->{barcode}|$rc_barcode\$/) {print \$0}'";
  my $sam2bamcmd = "samtools view -o '$reads_bam'" . ($fraction_reads ? " -s $fraction_reads " : " ") . "-";

  system('bash', '-c', "set -o pipefail; $viewcmd | $bcfiltercmd | $sam2bamcmd") == 0
    or die "ERROR: samtools view with barcode filtering failed: $?";
}
else {
  my @viewcmd = (
    "samtools",
    "view",
    "-o", $reads_bam,
    $fraction_reads ? ("-s", $fraction_reads) : (),
    "!{sampleBam}",
    $amplicon_location
  );

  # print Dumper(\@viewcmd);  # For debugging
  system(@viewcmd) == 0
    or die "ERROR: samtools view failed: $?";
}

# Use samtools to create a FASTQ file with reads that overlap this amplicon
my @fastqcmd = (
  "samtools",
  "fastq",
  "-0", $reads_fastq,
  $reads_bam
);
system(@fastqcmd) == 0
  or die "ERROR: samtools fastq failed: $?";

my @crispresso_cmd = !{Escape.cmdAsPerlList(task.ext.crispresso,
  '-r1', '$reads_fastq',
  '-a', '$info->{seq}',
  '-o', './$output_dir',
  '-w', task.ext.crispresso.window
)};

if ($info->{guide}) {
  $info->{guide} = join(',', @{$info->{guide}}) if ref($info->{guide}) eq 'ARRAY';
  push @crispresso_cmd, ('-g', $info->{guide});
}
if ($info->{HDR}) {
  $info->{HDR} = join(',', @{$info->{HDR}}) if ref($info->{HDR}) eq 'ARRAY';
  push @crispresso_cmd, ('-e', $info->{HDR});
}
if ($info->{coding}) {
  $info->{coding} = join(',', @{$info->{coding}}) if ref($info->{coding}) eq 'ARRAY';
  push @crispresso_cmd, ('-c', $info->{coding});
}

# print Dumper(\@crispresso_cmd);  # For debugging

system(@crispresso_cmd) == 0
  or die "ERROR: CRISPResso failed: $?";

# Rename the CRISPResso output directory
my $CRISPResso_out = "CRISPResso_output";
rename($output_dir . "/CRISPResso_on_reads", $output_dir . "/" . $CRISPResso_out);

# For easier browsing, link main CRISPResso output files to the top level directory
# with names that include sample and amplicon information, and compress alleles table.
my $sample_prefix = "!{meta.sampleName}_";
$sample_prefix .= "!{meta.readId}_" unless "!{meta.readId}" eq "Merged";
$sample_prefix .= $amplicon->{name} . "_";

opendir(my $CRISPResso_dir, $output_dir . "/" . $CRISPResso_out);
while (my $file = readdir($CRISPResso_dir)) {
  if ($file =~ /^2\..*pdf$/) {
    symlink($CRISPResso_out . "/" . $file, $output_dir . "/" . $sample_prefix . "Edit_frequency_pie_chart.pdf");
  }
  elsif ($file =~ /^9\.Alleles_around_cut_site_for_(.*).pdf/) {
    symlink($CRISPResso_out . "/" . $file, $output_dir . "/" . $sample_prefix . "Alleles_for_" . $1 . ".pdf");
  }
  elsif ($file =~ /^(Quantification|Frameshift).*txt$/) {
    symlink($CRISPResso_out . "/" . $file, $output_dir . "/" . $sample_prefix . $file);
  }
}
closedir($CRISPResso_dir);
