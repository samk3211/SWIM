#!/bin/bash

set term png
set output "membership.png"
set xdata time
set timefmt "[%H:%M:%S]"
set format x "%H:%M:%S"
set title 'Membership'
set xlabel "Time"
set ylabel "Nodes"
set pointsize 0.5

plot "./result.dat" using 1:2 title "Membership" lc rgb '#0025ad'

pause -1