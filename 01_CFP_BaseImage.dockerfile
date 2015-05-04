# ---------------- Base image starts here --------------------

FROM   ubuntu:12.04

MAINTAINER Mani <sadhak001@gmail.com>

ENV         PROJECT_NAME=cfp-devoxx-fr
ENV         ACTIVATOR_VERSION 1.3.2
ENV         DEBIAN_FRONTEND noninteractive
ENV         JAVA_HOME=/usr/lib/jvm/openjdk-7-jdk/

ENV         redisTargetFolder /root/redis/

# INSTALL OS DEPENDENCIES
RUN         apt-get update; apt-get install -y software-properties-common unzip wget \ 
            telnet git unzip pwgen ca-certificates

# Install Java 7
RUN \
  apt-get update && \
  apt-get install -y openjdk-7-jdk && \
  rm -rf /var/lib/apt/lists/*

# INSTALL TYPESAFE ACTIVATOR
RUN         cd /tmp && \
            wget http://downloads.typesafe.com/typesafe-activator/$ACTIVATOR_VERSION/typesafe-activator-$ACTIVATOR_VERSION.zip && \
            unzip typesafe-activator-$ACTIVATOR_VERSION.zip -d /usr/local && \
            mv /usr/local/activator-$ACTIVATOR_VERSION /usr/local/activator && \
            rm typesafe-activator-$ACTIVATOR_VERSION.zip

ENV         PATH=$PATH:/usr/local/activator

# Download and install Redis
RUN mkdir -p $redisTargetFolder                                \
    cd $redisTargetFolder                                      \
    wget http://download.redis.io/releases/redis-2.8.19.tar.gz \
    tar xcvf redis-2.8.19.tar.gz                               \
    cd redis-2.8.19                                            \
    make                                                       \ 
    make install

# Copy conf file for redis from host to container
ADD redis/*.conf $redisTargetFolder
RUN touch $redisTargetFolder/redis_uk.pid ; touch $redisTargetFolder/stdout_redis_uk.log.txt
RUN redis-server & \ 
    redis-cli -p 6366 &