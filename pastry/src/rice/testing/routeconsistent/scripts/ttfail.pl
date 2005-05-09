#!/usr/bin/perl

# calculates how long between when a node shuts down and its keyspace is taken over
# run as "perl ttfail.pl extracted > output"
# where extracted is the output of extract.pl

use warnings;
use strict;

our %times;

# read data
while (<>) {
  chomp;
  my ($file,$line,$type,$timestamp,$l,$us,$r) = split;

  # find interval
  if ($type eq "SHUTDOWN") {
    push @{$times{$timestamp}},["SHUTDOWN",$us,"SHUTDOWN"];
  } elsif ($type eq "1" or $type eq "2") {
    # LEAFSET 4 and 5 messages are from before the node is up

    push @{$times{$timestamp}},[$l,$us,$r];
  }
}

our %dead; # {id} -> $time_died;
our %world; # {id} -> {$l,$r}

my $ntimes = scalar(keys %times);
my $n = 0;
my $onepercent = int($ntimes / 100.0 + 0.5);

# walk through timeline
for my $t (sort { $a <=> $b } keys %times) {
  if ($n++ % $onepercent == 0) {
    printf STDERR "processing time $t (%d percent) [%s]\n", int($n / $ntimes * 100.0), join(' ',(times)[0,1]);
  }
  # check for deaths
  for my $e (@{$times{$t}}) {
      my ($l,$us,$r) = @$e;
      if ($l eq "SHUTDOWN") {
	  $dead{$us} = $t;
	  delete $world{$us};
      } else {
	  $world{$us} = $e;
      }
  }
  my %ghosts = %dead;
  # check to see if anybody dead is believed to be dead
  for my $e (values %world) {
      my ($l,$us,$r) = @$e;
      delete $ghosts{$l} if (exists $ghosts{$l});
      delete $ghosts{$r} if (exists $ghosts{$r});
  }

  # the guys remaining in ghosts are nobody's neighbors
  for my $k (keys %ghosts) {
      print(($t - $dead{$k}) ." $dead{$k} $t $k\n");
      delete $dead{$k};
  }
}

for my $k (keys %dead) {
    warn "$dead{$k} undead at end of test\n";
}
