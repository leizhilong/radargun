echo "make sure the following steps are finished before running this script"
echo "1. run ssh-keygen -t rsa"
echo "2. copy ~/.ssh/id_rsa.pub to remote host, rename it to authorized_keys and put it to ~/.ssh" 
echo "3. try to use scp manually to save id to knownhost" 

echo "distribuiting /RadarGun-1.1.0-SNAPSHOT/conf/mybenchmark.xml..."
scp ./framework/src/main/resources/mybenchmark.xml  infinispan@172.17.254.230:/home/infinispan/RadarGun-1.1.0-SNAPSHOT/conf/mybenchmark.xml
scp ./framework/src/main/resources/mybenchmark.xml  infinispan@172.17.254.231:/home/infinispan/RadarGun-1.1.0-SNAPSHOT/conf/mybenchmark.xml
scp ./framework/src/main/resources/mybenchmark.xml  infinispan@172.17.254.232:/home/infinispan/RadarGun-1.1.0-SNAPSHOT/conf/mybenchmark.xml
scp ./framework/src/main/resources/mybenchmark.xml  infinispan@172.17.254.233:/home/infinispan/RadarGun-1.1.0-SNAPSHOT/conf/mybenchmark.xml
scp ./framework/src/main/resources/mybenchmark.xml  infinispan@172.17.254.234:/home/infinispan/RadarGun-1.1.0-SNAPSHOT/conf/mybenchmark.xml


echo "distribuiting infinispan config file..."
scp ./plugins/infinispan53/src/main/resources/transaction-repl-sync.xml  infinispan@172.17.254.230:/home/infinispan/RadarGun-1.1.0-SNAPSHOT/plugins/infinispan53/conf/transaction-repl-sync.xml
scp ./plugins/infinispan53/src/main/resources/transaction-repl-sync.xml  infinispan@172.17.254.231:/home/infinispan/RadarGun-1.1.0-SNAPSHOT/plugins/infinispan53/conf/transaction-repl-sync.xml
scp ./plugins/infinispan53/src/main/resources/transaction-repl-sync.xml  infinispan@172.17.254.232:/home/infinispan/RadarGun-1.1.0-SNAPSHOT/plugins/infinispan53/conf/transaction-repl-sync.xml
scp ./plugins/infinispan53/src/main/resources/transaction-repl-sync.xml  infinispan@172.17.254.233:/home/infinispan/RadarGun-1.1.0-SNAPSHOT/plugins/infinispan53/conf/transaction-repl-sync.xml
scp ./plugins/infinispan53/src/main/resources/transaction-repl-sync.xml  infinispan@172.17.254.234:/home/infinispan/RadarGun-1.1.0-SNAPSHOT/plugins/infinispan53/conf/transaction-repl-sync.xml

scp ./plugins/infinispan53/src/main/resources/transaction-dist-sync.xml  infinispan@172.17.254.230:/home/infinispan/RadarGun-1.1.0-SNAPSHOT/plugins/infinispan53/conf/transaction-dist-sync.xml
scp ./plugins/infinispan53/src/main/resources/transaction-dist-sync.xml  infinispan@172.17.254.231:/home/infinispan/RadarGun-1.1.0-SNAPSHOT/plugins/infinispan53/conf/transaction-dist-sync.xml
scp ./plugins/infinispan53/src/main/resources/transaction-dist-sync.xml  infinispan@172.17.254.232:/home/infinispan/RadarGun-1.1.0-SNAPSHOT/plugins/infinispan53/conf/transaction-dist-sync.xml
scp ./plugins/infinispan53/src/main/resources/transaction-dist-sync.xml  infinispan@172.17.254.233:/home/infinispan/RadarGun-1.1.0-SNAPSHOT/plugins/infinispan53/conf/transaction-dist-sync.xml
scp ./plugins/infinispan53/src/main/resources/transaction-dist-sync.xml  infinispan@172.17.254.234:/home/infinispan/RadarGun-1.1.0-SNAPSHOT/plugins/infinispan53/conf/transaction-dist-sync.xml

echo "distribuiting jgroups config file..."
scp ./plugins/infinispan53/src/main/resources/jgroups/jgroups.xml infinispan@172.17.254.230:/home/infinispan/RadarGun-1.1.0-SNAPSHOT/plugins/infinispan53/conf/jgroups/jgroups.xml 
scp ./plugins/infinispan53/src/main/resources/jgroups/jgroups.xml infinispan@172.17.254.231:/home/infinispan/RadarGun-1.1.0-SNAPSHOT/plugins/infinispan53/conf/jgroups/jgroups.xml 
scp ./plugins/infinispan53/src/main/resources/jgroups/jgroups.xml infinispan@172.17.254.232:/home/infinispan/RadarGun-1.1.0-SNAPSHOT/plugins/infinispan53/conf/jgroups/jgroups.xml 
scp ./plugins/infinispan53/src/main/resources/jgroups/jgroups.xml infinispan@172.17.254.233:/home/infinispan/RadarGun-1.1.0-SNAPSHOT/plugins/infinispan53/conf/jgroups/jgroups.xml 
scp ./plugins/infinispan53/src/main/resources/jgroups/jgroups.xml infinispan@172.17.254.234:/home/infinispan/RadarGun-1.1.0-SNAPSHOT/plugins/infinispan53/conf/jgroups/jgroups.xml 