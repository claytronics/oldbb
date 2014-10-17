#!/usr/bin/perl

my @input = ();
while (<>) {
    push(@input, $_);
}
$lastline = "";
%prev = ();
%state = ();
foreach $line (@input) {
    ($lastline eq $line) && next;
    if ($line =~ /^[0-9]+:(.*)/) {
	($node, $rest) = $line =~ /^([0-9]+):(.*)/;
	if ($prev{$node} eq $rest) {
	    $line = "";
	} else {
	    $prev{$node} = $rest;
	}
	if ($rest =~ /<[^>]+>/) {
	    ($state) = $rest =~ /<([^>]+)>/;
	    $state{$node} = $state;
	}
    }
    print $line;
    $lastline = $line;
}
foreach $n (sort { $a <=> $b } keys %state) {
    print "$n\t".$state{$n}."\n";
}
exit(0);

