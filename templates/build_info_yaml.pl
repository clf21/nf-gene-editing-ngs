#!/usr/bin/env perl

use strict;
use warnings;
use YAML::XS qw(LoadFile DumpFile);

# Either "yaml" or "raw", for dumping YAML files or raw values, respectively
my $payload_mode = "!{payloadMode}";

my $delimiter = "!{params._internal.delimiter}";

# TODO Injected inputs need to be escaped
my @yaml_routes = qw(!{yamlRoutes.join(" ")});
my @payloads = qw(!{payloads.join(" ")});

my %output;

# Iterate through the routes to reconstruct the structure
for (my $i = 0; $i < @yaml_routes; $i++) {
  my $parent = \%output;
  my @route = split(/\Q$delimiter\E/, $yaml_routes[$i]);

  for (my $j = 0; $j < @route; $j++) {
    my $path = $route[$j];
    my $is_leaf = $j == @route - 1;

    if (!exists $parent->{$path}) {
      if ($is_leaf) {
        if ($payload_mode eq "yaml") {
          $parent->{$path} = LoadFile($payloads[$i]);
        } elsif ($payload_mode eq "raw") {
          $parent->{$path} = $payloads[$i];
        }
      } else {
        $parent->{$path} = {};
      }
    }

    $parent = $parent->{$path};
  }
}

DumpFile("info.yaml", \%output);
