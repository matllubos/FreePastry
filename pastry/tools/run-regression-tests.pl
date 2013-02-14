#!/usr/bin/perl
# usage: ./run-regression-tests pastry.jar

use warnings;
use strict;

our $jar; 

if (@ARGV) {
	$jar = shift @ARGV;
} else {
	die "usage: $0 freepastry.jar\n";
}

my $autobuild_dir = '/DS/usr/jeffh/pastry/autobuild';

our $java_exe = "/usr/bin/java -cp $autobuild_dir/pastry/lib/xpp3-1.1.3.4d_b2.jar:$autobuild_dir/pastry/lib/xmlpull_1_1_3_4a.jar:$jar";
our $logdir;

our $timeout = 900; # seconds

our @attachments;
our $msg;
our $fail_count;

our @commonapi_tests = qw(
rice.p2p.scribe.testing.ScribeRegrTest
rice.p2p.scribe.testing.RawScribeRegrTest
rice.p2p.past.testing.PastRegrTest
rice.p2p.past.testing.RawPastRegrTest
rice.p2p.splitstream.testing.SplitStreamRegrTest
rice.p2p.replication.testing.ReplicationRegrTest
rice.p2p.replication.manager.testing.ReplicationManagerRegrTest
);

sub out {
	my $s = shift;
	
	print $s;
	$msg .= $s;
}

sub system_with_timeout {
  my $timeout = shift;
  my @exec_args = @_;
  my $pid;

#  warn "system_with_timeout: forking";
  die "Can't fork!" unless defined ($pid=fork);

  if ($pid) {
    # parent
    eval {
      local $SIG{ALRM} = sub { die "alarm\n"; }; # NB \n required
      alarm $timeout;
#      warn "system_with_timeout: waiting up to $timeout seconds on child";
      waitpid $pid, 0;
#      warn "system_with_timeout: waiting child completed with result $?";
      alarm 0;
    };
    if ($@) {
#      warn "system_with_timeout: died with $@";
      die unless $@ eq "alarm\n";   # propagate unexpected errors
      # timed out
      
#      warn "system_with_timeout: child timed out";

#      warn "system_with_timeout: sending TERM";
      kill "TERM", $pid;
#      warn "system_with_timeout: sleeping 5";
      sleep 5;
#      warn "system_with_timeout: sending KILL (for good measure)";
      kill "KILL", $pid;
#      warn "system_with_timeout: reaping (this should be instantaneous)";
      waitpid $pid, 0;  # reap it
#      warn "system_with_timeout: reaped";
    }
  } else {
#    warn "system_with_timeout: child execing '@exec_args'";
    exec @exec_args;
    exit; # actually can't reach here
  }
}

sub run_commonapi_test {
	my ($test,$proto,$nodes) = @_;
	my $pid;

	my $testname="$test-$proto-$nodes";
	
	my $exec = "$java_exe $test -protocol $proto -nodes $nodes 2>&1 >$logdir/$testname.out";
	print "running $exec\n";
	system_with_timeout($timeout,$exec);
	
	my $ret = $?;
	
	if ($ret) {
		out "FAIL: $testname [$ret]\n";
		push @attachments, $testname;
		$fail_count++;
	} else {
		out "$testname succeeded\n";
	}
}

sub random_node_distro {
  return int(10+8*(rand()*4.7)**2);
}

sub random_node_distro2 {
  return int(10+(rand()*20));
}

sub run_commonapi_tests {
  for my $test (@commonapi_tests) {
	  run_commonapi_test($test,"direct",random_node_distro());
	  run_commonapi_test($test,"socket",random_node_distro2());
  }
}

# main

# process args
my $TODAY = `date +%Y%m%d`;
my $date = `date -R`;
chomp $TODAY;
chomp $date;
$logdir = "$autobuild_dir/logs/$TODAY";

run_commonapi_tests();

if ($fail_count) {
	# compute date, today
	my $recipient = "jeffh\@mpi-sws.mpg.de,jstewart\@mpi-sws.mpg.de"; # "Pastry Team <freepastry\@cs.rice.edu>";
	my $whole_msg = <<EOM;
From: Automatic Build Server <no-reply\@mpi-sws.mpg.de>
To: $recipient
Subject: [pastry-build] Nightly regression tests $TODAY failed
Date: $date
Content-Type: multipart/mixed; boundary="---------MONKEYBUTTER"

This is a multipart message in MIME format.
-----------MONKEYBUTTER
Content-Type: text/plain; charset=US-ASCII; format=flowed

$fail_count regression tests failed

$msg

See attachments for details.
EOM

	for my $test (@attachments) {
		my $content = `cat $logdir/$test.out`;
		$whole_msg .= <<EOM;
-----------MONKEYBUTTER
Content-Type: text/plain; name=$test-$TODAY.txt
Content-Description: regression test $test output
Content-Disposition: attachment; filename=$test-$TODAY.txt

$content
EOM
	}

	$whole_msg .= "-----------MONKEYBUTTER--\n";
	
	open my $fh, "|sendmail $recipient";
	print $fh $whole_msg;
}
