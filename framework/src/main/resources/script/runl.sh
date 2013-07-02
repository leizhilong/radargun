rm *.out
rm *.log
./bin/local.sh -c ./conf/local-benchmark.xml
sleep 1
tail -f radargun.log