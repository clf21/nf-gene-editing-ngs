#!/usr/bin/env perl

use strict;
use warnings;

use File::Temp qw(tempdir);
use File::Basename;
use Sys::Hostname;
use YAML::XS qw(DumpFile);

my $PROGNAME = basename($0);

# Configuration (with defaults) from environment
my $OUTPUT = $ENV{'OUTPUT'} || 'error.yaml';
my $CONTEXT = ($ENV{'CONTEXT'} || 3) + 0;

sub tail {
  # Return the trailing lines of a file
  #
  # Parameters:
  #   $file     string   The filename to tail
  #   $context  integer  The number of lines of context
  my ($file, $context) = @_;

  my @tail = ();

  open my $fh, "<", $file or die "Failed to open file '$file' for reading: $!";
  while (<$fh>) {
    push(@tail, $_);
    shift @tail if @tail > $context;
  }
  close $fh;

  return join("", @tail);
}

sub capture {
  # Executes a command, with the provided arguments, capturing its
  # output and exit status
  #
  # Parameters:
  #   $cmd   string  The command to be executed
  #   @args  list    Command line arguments (optional)
  my ($cmd, @args) = @_;

  my $workdir = tempdir(CLEANUP => 1);
  my $stdout = "$workdir/stdout";
  my $stderr = "$workdir/stderr";

  die "Failed to fork: $!" unless defined(my $pid = fork);

  if ($pid == 0) {
    # Duplicate stdout and stderr to files
    open(STDOUT, "|-", "tee \"${stdout}\"");
    open(STDERR, "|-", "tee \"${stderr}\" >&2");

    exec($cmd, @args) or die "Failed to execute command: $!";
  }

  # Wait for child process
  waitpid($pid, 0);
  my $exit_code = $? >> 8;

  return ($exit_code, $stdout, $stderr);
}

sub main {
  if (@ARGV == 0) {
    # We need at least one argument
    # IMPORTANT: Don't exit with a non-zero status, even on failure
    print STDERR "Usage: $PROGNAME COMMAND [OPTIONS...]\n";
    exit;
  }

  my ($cmd, @args) = @ARGV;
  my ($exit_code, $stdout, $stderr) = capture($cmd, @args);

  if ($exit_code) {
    print STDERR "Command failed; writing error output to '$OUTPUT'\n";

    DumpFile($OUTPUT, {
      host_name => hostname,
      command => $cmd,
      arguments => [@args],
      exit_code => $exit_code,
      stdout => tail($stdout, $CONTEXT),
      stderr => tail($stderr, $CONTEXT)
    });
  }
}

main() unless caller;
