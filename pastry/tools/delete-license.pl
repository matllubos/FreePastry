#!/usr/bin/perl -w

#delete-license.pl 
#usage ./delete-license.pl oldlicensefile src-dir

use warnings;
use strict;

our $licensefile = shift;
our $src_dir = shift;

our $license_text = `cat $licensefile`;
our $license_length;

my $tmp = `wc -l $licensefile`;
if ($tmp =~ /^(\d+)/) {
	$license_length = $1 + 1;
} else {
	die "bogus: $tmp";
}

$license_text = quotemeta($license_text); #escape the license

#print "$license_text\n";

our @needs_license;
our $msg;
my $numFiles = 0;

open FILEHANDLE, "find $src_dir \\\( -name '.svn' -prune \\\) -o \\\( -name '*.java' -print \\\) |";
while (<FILEHANDLE>) {
	 chomp $_; 
	 my $java_file = $_;
#	 print "$java_file\n"; 
	 $numFiles++;
	 
	 my $java_contents = `cat ./$java_file`;
#	 $java_contents = quotemeta($java_contents);
	 if ($java_contents =~ /^$license_text/) {
	 # now we know it has the license
            print "./$java_file ./$java_file\.bak $license_length\n";
	    `mv ./$java_file ./$java_file\.bak`;
	    `tail +$license_length ./$java_file\.bak > ./$java_file`;
            `rm ./$java_file\.bak`;
	 } else {
            print "$java_file did not have the old license.\n";
	 }
}
