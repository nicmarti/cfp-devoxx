# ---------------- Play application image starts here --------------------

FROM cfp-base-image:1.0

MAINTAINER Mani <sadhak001@gmail.com>

ENV         PROJECT_NAME=cfp-devoxx-fr
ENV         ACTIVATOR_VERSION 1.3.2
ENV         DEBIAN_FRONTEND noninteractive
ENV         JAVA_HOME=/usr/lib/jvm/openjdk-7-jdk/

ENV         cfpTargetFolder /root/cfp-devoxx-fr/
ENV         APPDIR $cfpTargetFolder/app

# Copy cfp-devoxx-fr source files from host into container
RUN mkdir -p $cfpTargetFolder

# Set Working directory
WORKDIR     $cfpTargetfolder

# ADD . /cfp-devoxx-fr
ADD LICENSE.txt  $cfpTargetFolder 
ADD README.md    $cfpTargetFolder 
ADD Vagrantfile  $cfpTargetFolder 
ADD activator-sbt-echo-play-shim.sbt \ 
                 $cfpTargetFolder 
ADD scalaio-vagrant.sh               \
                 $cfpTargetFolder

ADD startCfpApp.sh \
                 $cfpTargetFolder 

ADD app         $cfpTargetFolder/app
ADD clevercloud $cfpTargetFolder/clevercloud
ADD conf        $cfpTargetFolder/conf
ADD lib         $cfpTargetFolder/lib
ADD logs        $cfpTargetFolder/logs
ADD lua_script  $cfpTargetFolder/lua_script
ADD project     $cfpTargetFolder/project
ADD public      $cfpTargetFolder/public
ADD test        $cfpTargetFolder/test

# Expose ports 8888 and 9000 and map them to host ports

EXPOSE          8888 9000

# TEST AND BUILD THE PROJECT -- FAILURE WILL HALT IMAGE CREATION
#RUN         cd $cfpTargetFolder/project; /usr/local/activator/activator new $PROJECT_NAME play-scala ; /usr/local/activator/activator play clean stage exit

RUN         ls -lash $cfpTargetFolder
#RUN         cd $cfpTargetFolder/project;
ENTRYPOINT $cfpTargetFolder/startCfpApp.sh $cfpTargetFolder