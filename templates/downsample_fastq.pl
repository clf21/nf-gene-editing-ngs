#!/usr/bin/env perl

use strict;
use warnings;
use IO::Uncompress::Gunzip;
use IO::Compress::Gzip;
use YAML::XS qw(LoadFile DumpFile);
# use Data::Dumper;  # For debugging

# Template tags:
#   task.ext.maxReads)
#     Maximum number of reads to keep after downsampling
#   task.ext.seed)
#     Seed for the pseudorandom number generator
#     The downsampling should be deterministic wrt this parameter
#   fastqFile)
#     FASTQ file of sample read
#
# Output - Files stored in `$PWD`:
#   `downsampled.fastq.gz`)
#     Downsampled reads
#   `info.yaml`)
#     Additional details about downsampling

my $input_file = "!{fastqFile}";
my $output_file = "downsampled.fastq.gz";

# Open input file handle depending on the suffix
my $in_handle;
if (substr($input_file, -2) eq "gz") {
  $in_handle = new IO::Uncompress::Gunzip "$input_file", MultiStream => 1;
}
else {
  open($in_handle, '<', "$input_file");
}
# Make sure the input file handle was opened
unless($in_handle) {
  die "Error: could not open input file $input_file.";
}

# Open output file handle depending on the suffix
my $out_handle;
if (substr($output_file, -2) eq "gz") {
  $out_handle = new IO::Compress::Gzip "$output_file", Time => 0;
}
else {
  open($out_handle, '>', "$output_file");
}
# Make sure the output file handle was opened
unless($out_handle) {
  die "Error: could not open output file $output_file.";
}

# Array of record numbers that will be kept from the input file, and
# hash with corresponding FASTQ record.
my @keep_record_nums = ();
my %keep_records = ();

# Counters for how many records and lines have been processed so far.
my $num_records = 0;
my $num_lines = 0;

# The input file will be sampled using reservoir sampling,
# which reads were kept will be stored in %keep_records
# and tracked in @keep_record_nums.

print STDERR "Downsampling file $input_file...";

# Initialize pseudo random number generator.
srand(!{task.ext.seed});

# Initialize FASTQ record
my $fastq_record = "";

while (my $line = readline($in_handle)) {
  # Build up the FASTQ record (4 lines) one line at a time
  $fastq_record .= $line;

  # Increment line counter
  $num_lines++;

  # Print a dot every 100000 records (ie 400000 lines)
  print STDERR "." if $num_lines % 400000 == 0;

  # Full record has been saved into $fastq_record every 4 lines
  if ($num_lines % 4 == 0) {
    # Do the reservoir sampling to select records to keep
    if ($num_records < !{task.ext.maxReads}) {
      push(@keep_record_nums, $num_records);
      $keep_records{$num_records} = $fastq_record;
    }
    else {
      my $i = int(rand($num_records + 1));
      if ($i < !{task.ext.maxReads}) {
        # Remove old record
        delete($keep_records{$keep_record_nums[$i]});
        # Store new record
        $keep_record_nums[$i] = $num_records;
        $keep_records{$num_records} = $fastq_record;
      }
    }

    # Reset fastq record
    $fastq_record = "";

    # Increment record count
    $num_records++;
  }
}

# Report that file reading is finished.
print STDERR " done\n";

# Write out the records that were kept after sampling, using
# the same order that the records were in originally.
foreach my $record_num (sort {$a <=> $b} keys(%keep_records)) {
  print {$out_handle} $keep_records{$record_num};
}

# Close the file handles.
close($in_handle);
close($out_handle);

# Report how many records were kept out of the total read.
print STDERR "Kept " . @keep_record_nums . " out of $num_records FASTQ records from file $input_file\n";

# Store information about the downsampling
DumpFile('info.yaml', {
  reads_before_downsampling => $num_records,
  reads_after_downsampling => scalar(@keep_record_nums)
});
