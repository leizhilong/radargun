echo "make sure the following steps are finished before running this script"
echo "1. run ssh-keygen -t rsa"
echo "2. copy ~/.ssh/id_rsa.pub to remote host, rename it to authorized_keys and put it to ~/.ssh" 
echo "3. try to use scp manually to save id to knownhost" 

scp ./target/distribution/RadarGun-1.1.0-SNAPSHOT.zip  infinispan@172.17.254.230:/home/infinispan
scp ./target/distribution/RadarGun-1.1.0-SNAPSHOT.zip  infinispan@172.17.254.231:/home/infinispan
scp ./target/distribution/RadarGun-1.1.0-SNAPSHOT.zip  infinispan@172.17.254.232:/home/infinispan
scp ./target/distribution/RadarGun-1.1.0-SNAPSHOT.zip  infinispan@172.17.254.233:/home/infinispan
scp ./target/distribution/RadarGun-1.1.0-SNAPSHOT.zip  infinispan@172.17.254.234:/home/infinispan