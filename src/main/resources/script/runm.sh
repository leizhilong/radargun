rm *.out
rm *.log
./bin/master.sh -m 172.17.254.230 -t
sleep 1
tail -f radargun.log