#!/bin/bash

set term png
set output "suspectedalive.png"
set xdata time
set timefmt "[%H:%M:%S]"
set format x "%H:%M:%S"
set title "Suspected/Alive"
set xlabel "Time"
set ylabel "Nodes"
set pointsize 0.5

plot "./result.dat" using 1:5 title "Suspected"   lc rgb '#FFB300', "./result.dat" using 1:6 title "Alive"  lc rgb '#7DE657'

pause -1