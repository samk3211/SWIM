#!/bin/bash

set term png
set output "newdead.png"
set xdata time
set timefmt "[%H:%M:%S]"
set format x "%H:%M:%S"
set title 'New/Dead'
set xlabel "Time"
set ylabel "Nodes"
set pointsize 0.5

plot './result.dat' using 1:3 title 'New'  lc rgb '#469EF6', './result.dat' using 1:4 title "Dead"  lc rgb '#F64646'

pause -1
