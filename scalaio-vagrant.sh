#!/usr/bin/env bash
USER=$1

apt-get update -y
apt-get upgrade -y
apt-get install -y python-software-properties
add-apt-repository -y ppa:webupd8team/java
add-apt-repository -y ppa:chris-lea/redis-server
apt-get update -y
echo oracle-java8-installer shared/accepted-oracle-license-v1-1 select true | /usr/bin/debconf-set-selections
apt-get install -y curl git gdebi-core oracle-java8-installer redis-server
echo oracle-java8-installer shared/accepted-oracle-license-v1-1 select true | /usr/bin/debconf-set-selections
cp /etc/redis/redis.conf /etc/redis/redis.conf.default
cp /home/vagrant/cfp/conf/redis-devoxxfr.conf /etc/redis/redis.conf

cd /tmp
wget https://download.elasticsearch.org/elasticsearch/elasticsearch/elasticsearch-1.1.0.deb >/dev/null
gdebi -n elasticsearch-1.1.0.deb >/dev/null

wget http://dl.bintray.com/sbt/debian/sbt-0.13.2.deb >/dev/null
gdebi -n sbt-0.13.2.deb >/dev/null
service  elasticsearch start
service  redis start
cd /home/$USER/cfp
su -l $USER -c 'echo "starting actions as $USER"'
if [ 0 -eq $? ] ; then
  su -l $USER -c "sbt update"
else
  echo "failed to act as logged $USER"
fi
