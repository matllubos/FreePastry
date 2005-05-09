#!/usr/bin/perl

# gets the relevant data out of the log files for consistency analysis
# run as "perl extract.pl log* > extracted"

use warnings;
use strict;

our %index;

our $rxid = qr/<(0x[[:xdigit:]]+)\.\.>/i;

sub get_leafset {
  my $line = shift;
  my @left;
  my $center;
  my @right;

#  print STDERR "line: $line\n\n";
  
  # set the pos() to the start of interesting stuff
  $line =~ m/leafset:\s*/g or die "why didn't you give me a leafset?";

#  print STDERR "pos: " . pos($line) . " \"".substr($line,pos($line),6)."...\"\n";
  while ($line =~ m/\G$rxid\s*/gc) {
    push @left, $1;
  }
#  print STDERR "matched " . scalar(@left) . " left members\n";
#  print STDERR "pos: " . pos($line) . " \"".substr($line,pos($line),6)."...\"\n";
  if ($line =~ m/\G\[ $rxid \]\s*/gc) {
    $center = $1;
  } else {
    die "the center cannot hold";
  }
  while ($line =~ m/\G$rxid\s*/gc) {
    push @right, $1;
  }

  return (\@left,$center,\@right);
}

our ($out_fh,$out_id) = (undef,"");

our $clock = undef;
our $lastid = undef;
our $lastfile = undef;
our $lastline = undef;

while (<>) {
  chomp;
  if (/^COUNT: (\d+)/) {
    $clock = $1;
    $lastfile = $ARGV;
    $lastline = $.;
    next;
  }
  if (/^LEAFSET(\d+)/) {
    my $type = $1;
    my $timestamp = (split /:/,$_,3)[1];
    my ($l,$us,$r) = get_leafset($_);

    if ($us ne $out_id) {
      close $out_fh if defined $out_fh;
      open $out_fh, ">ls.$us.txt" or die "$!";
      $out_id = $us;
    }
    print $out_fh "$timestamp ".join(' ',@$l)." [ $us ] ".join(' ',@$r) . "\n";

    if (scalar(@$l)==0 or scalar(@$r)==0) {
      warn "$ARGV:$. bad line at time $timestamp. leafset failure?\n";
    } else {
      print "$ARGV $. $type $timestamp $l->[-1] $us $r->[0]\n";
    }
    $lastid = $us;
    $clock = $timestamp;
    $lastfile = $ARGV;
    $lastline = $.;
    next;
  }
  if ((m/^STARTUP/ or m/^CJP:/) and m|$rxid/([A-Za-z0-9_.-]+)/([0-9.:]+)|) {
    my ($node_id,$hostname,$ip) = ($1,$2,$3);
    warn "duplicate node id: $node_id $hostname $ip\n" 
      if exists($index{$node_id}) and 
	($index{$node_id}[0] ne $hostname or $index{$node_id}[1] ne $ip);
    $index{$node_id} = [$hostname,$ip];
    next;
  }
  if (/^SHUTDOWN (\d+).*$rxid/) {
    # add which nodeid we think it is shutting down
    print "$ARGV $. SHUTDOWN $1 0 $2 0\n";
    $clock = undef;
    next;
  }
  if (/^Socket: Contacting bootstrap node/ and defined($clock) and defined($lastid)) {
    print "$lastfile $lastline SHUTDOWN $clock 0 $lastid 0\n";
    next;
  }
} continue {
    if (eof) {     # Not eof()!
      close ARGV;  # fix line numbering
    }
}

if (defined($clock) and defined($lastid)) {
    print "$lastfile $lastline SHUTDOWN $clock 0 $lastid 0\n";
}

open INDEX, ">node_index.txt" or die "$!";

for my $i (sort {hex($a) <=> hex($b)} keys %index) {
  print INDEX "$i " . join(' ',@{$index{$i}}) . "\n";
}

close INDEX;
