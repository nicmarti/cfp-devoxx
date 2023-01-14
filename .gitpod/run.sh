#!/bin/sh

export JAVA_HOME=/home/gitpod/.sdkman/candidates/java/current/

export SBT_OPTS="-DCRON_UPDATER=false -DBITBUCKET_PASSWORD=TODO -DLINKEDIN_SECRET=TODO"
export SBT_OPTS="$SBT_OPTS -Djline.terminal=none -DBUCKET_ROOTFOLDER=TODO -DGITHUB_SECRET=TODO"
export SBT_OPTS="$SBT_OPTS -Dsbt.global.base=/tmp/sbt-global-pluginstub -DACTIVATE_HTTPS=false -DES_ADDON_USER="
export SBT_OPTS="$SBT_OPTS -DDIGEST_WEEKLY=1 -DGOOGLE_SECRET=TODO -DCFP_LANG=fr,fr-FR,en,en-US -DACTIVATE_VOTE=true"
export SBT_OPTS="$SBT_OPTS -DSMTP_HOST=in-v3.mailjet.com -DOPSGENIE_API=TODO -DSBT_SCALA_VERSION=2.10.4"
export SBT_OPTS="$SBT_OPTS -DBITBUCKET_URL=https://bitbucket.org/api/1.0/repositories/TODO/TODO/issues"
export SBT_OPTS="$SBT_OPTS -DOPSGENIE_NAME=TODO -Dsbt.log.noformat=true -Dfile.encoding=UTF8"
export SBT_OPTS="$SBT_OPTS -DMAIL_BCC=TODO@devoxx.fr -DES_ADDON_URI=http://localhost:9200 -DREDIS_PASSWORD="
export SBT_OPTS="$SBT_OPTS -DREDIS_HOST=localhost -DMAIL_COMMITTEE=TODO@devoxx.fr -DES_ADDON_HOST=TODO"
export SBT_OPTS="$SBT_OPTS -DSMTP_USER=TODO -DCFP_HOSTNAME=localhost:9000 -DCRON_DAYS=2 -DBITBUCKET_USERNAME=TODO"
export SBT_OPTS="$SBT_OPTS -DMAIL_FROM=TODO@devoxx.fr -DDIGEST_DAILY=08:00 -DSMTP_MOCK=true -DCFP_IS_OPEN=true"
export SBT_OPTS="$SBT_OPTS -DES_ADDON_PORT=1234 -DACTIVATE_GOLDEN_TICKET=true -DREDIS_PORT=6379"
export SBT_OPTS="$SBT_OPTS -DLINKEDIN_CLIEND_ID=TODO -DSMTP_SSL=false -DSMTP_PASSWORD=TODO -DGITHUB_ID=TODO"
export SBT_OPTS="$SBT_OPTS -DAPP_SECRET=1f0bc136-5c99-11e6-bee8-60f81dae2bbc -DGOOGLE_ID=TODO.apps.googleusercontent.com"
export SBT_OPTS="$SBT_OPTS -DSMTP_PORT=587 -DES_ADDON_PASSWORD= -DMAIL_BUG_REPORT=TODO@devoxx.fr"
export SBT_OPTS="$SBT_OPTS -Xrunjdwp:transport=dt_socket,server=y,suspend=n,address=9999 -Xms512M -Xmx1024M -Xss1M"
export SBT_OPTS="$SBT_OPTS -XX:+CMSClassUnloadingEnabled -Didea.managed=true"

/home/linuxbrew/.linuxbrew/opt/sbt@0.13/bin/sbt run
