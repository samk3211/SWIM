#!/bin/bash

set term png
set output "parents.png"
set xdata time
set timefmt "[%H:%M:%S]"
set format x "%H:%M:%S"
set title "New/Dead Parents"
set xlabel "Time"
set ylabel "Nodes"
set pointsize 0.5

plot "./result.dat" using 1:7 title "NewParent"   lc rgb '#7DE657', "./result.dat" using 1:8 title "DeadParent"  lc rgb '#FFB300'

pause -1