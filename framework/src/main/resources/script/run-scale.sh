rm *.out
rm *.log
./bin/master.sh -m 172.17.254.230 -c ./conf/resilience-benchmark.xml
sleep 2
tail -f radargun.log