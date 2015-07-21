#!/bin/bash

echo "Executing simulation. Please, be patient..."

date1=$(date +"%s")

java -jar ./swim-project-1.0-SNAPSHOT.jar $1 > ./log.txt

echo "Simulation complete!"

date2=$(date +"%s")
diff=$(($date2-$date1))
echo "$(($diff / 60)) minutes and $(($diff % 60)) seconds elapsed."


echo "Reading the log..."

egrep ">>>" -i ./log.txt > tmp
egrep "PBAlive" ./log.txt > a
egrep "PBDead" ./log.txt > d
egrep "PBNew" ./log.txt > n
egrep "PBSuspected" ./log.txt > s
egrep "PBParentNew" ./log.txt > np
egrep "PBParentDead" ./log.txt > dp


rm ./data.txt &>/dev/null

echo "Creating temporary files..."

awk '{
print $1 "; \t" $9;
}
' tmp > data.txt
rm ./tmp


rm ./dataA.txt &>/dev/null
awk '{
print $6;
}
' a > dataA.txt
rm ./a

rm ./dataN.txt &>/dev/null
awk '{
print $6;
}
' n > dataN.txt
rm ./n

rm ./dataD.txt &>/dev/null
awk '{
print $6;
}
' d > dataD.txt
rm ./d

rm ./dataS.txt &>/dev/null
awk '{
print $6;
}
' s > dataS.txt
rm ./s



rm ./dataNP.txt &>/dev/null
awk '{
print $6;
}
' np > dataNP.txt
rm ./np

rm ./dataDP.txt &>/dev/null
awk '{
print $6;
}
' dp > dataDP.txt
rm ./dp


rm ./tmp0.txt &>/dev/null
rm ./tmp1.txt &>/dev/null
rm ./tmp2.txt &>/dev/null
rm ./tmp3.txt &>/dev/null
rm ./tmp4.txt &>/dev/null
rm ./result.txt &>/dev/null

echo "Processing info for DAT file..."


awk 'FNR==NR  { a[FNR""] = $0; next }  { print a[FNR""], $0 }' data.txt dataN.txt > tmp0.txt
awk 'FNR==NR { a[FNR""] = $0; next }  { print a[FNR""], $0 }' tmp0.txt dataD.txt > tmp1.txt
awk 'FNR==NR { a[FNR""] = $0; next }  { print a[FNR""], $0 }' tmp1.txt dataS.txt > tmp2.txt
awk 'FNR==NR { a[FNR""] = $0; next }  { print a[FNR""], $0 }' tmp2.txt dataA.txt > tmp3.txt
awk 'FNR==NR { a[FNR""] = $0; next }  { print a[FNR""], $0 }' tmp3.txt dataNP.txt > tmp4.txt
awk 'FNR==NR { a[FNR""] = $0; next }  { print a[FNR""], $0 }' tmp4.txt dataDP.txt > result1.dat

awk 'gsub(/\,/,".")' result1.dat > result.dat
rm ./result1.dat &>/dev/null

rm ./SMAData.dat &>/dev/null

echo "Cleaning up..."

rm ./tmp0.txt &>/dev/null
rm ./tmp1.txt &>/dev/null
rm ./tmp2.txt &>/dev/null
rm ./tmp3.txt &>/dev/null
rm ./tmp4.txt &>/dev/null
rm ./tmp0c.txt &>/dev/null
rm ./tmp1c.txt &>/dev/null
rm ./tmp2c.txt &>/dev/null
rm ./dataA.txt &>/dev/null
rm ./dataD.txt &>/dev/null
rm ./dataN.txt &>/dev/null
rm ./dataS.txt &>/dev/null
rm ./dataNP.txt &>/dev/null
rm ./dataDP.txt &>/dev/null
rm ./data.txt &>/dev/null