#!/bin/bash
# copy this file to run.sh
# make it executable
# updat all values
# ./run.sh will start play and SBT
# you can then work in development mode with ~run.sh
#
# Do not commit run.sh on your Github repo
# Use environment variables for production on Clever-cloud

export APP_SECRET="?Ldferthg2dfhKhaBVK8wgyJpd4mDnBstvjaRrKC^7<^u9MRp5Tp"
export CFP_LANG="en,en-US"
export CFP_HOSTNAME="cfp.devoxx.co.uk"

export SMTP_HOST="in.mailjet.com"
export SMTP_USER="8cfa252b19ce55ef8d337228d3453323"
export SMTP_PASSWORD="fb6abcfe0e9c971e2e36eae27d705ad5"
export SMTP_SSL="yes"
export SMTP_PORT="587"
export SMTP_MOCK="yes"

export MAIL_BCC="nicolas.martignole@devoxx.fr"
export MAIL_COMMITTEE="nicolas.martignole@devoxx.fr"
export MAIL_FROM="nicolas.martignole@devoxx.fr"
export MAIL_BUG_REPORT="nicolas.martignole@devoxx.fr"

export LINKEDIN_CLIEND_ID="77lzme1q1ol7dl"
export LINKEDIN_SECRET="jSjKS9hecOIjdhmQ"

export GITHUB_ID="22f66f8d74263d6b290f"
export GITHUB_SECRET="80ec3a121df03b9a516db666f8a8ac4ccad09dba"

export GOOGLE_ID="4508927123-gv55qihanik6tb3tol76m0jslhal8alq.apps.googleusercontent.com"
export GOOGLE_SECRET="nhTWLDrAr4fscpYUMU_yoaFy"

export REDIS_HOST="localhost"
export REDIS_PORT="6366"
export REDIS_PASSWORD=''

export CRON_UPDATER="false"
export CRON_DAYS="2"

export ES_ADDON_URI="http://localhost:9200"
export ES_ADDON_USER=""
export ES_ADDON_PASSWORD=""

export BITBUCKET_USERNAME="TODO"
export BITBUCKET_PASSWORD="TODO"
export BITBUCKET_URL="https://bitbucket.org/api/1.0/repositories/TODO/TODO/issues"

export OPSGENIE_API=""
export OPSGENIE_NAME=""

export CFP_IS_OPEN="true"

export ACTIVATE_GOLDEN_TICKET="true"
export ACTIVATE_HTTPS="false"

# Set to false to close/unactivate the fav system in development mode
# For Prod => it has to be done on Clever-cloud
export ACTIVATE_FAVORITES="true"
export ACTIVATE_VOTE="true"

export BUCKET_ROOTFOLDER="$HOME/redis/cfp.devoxx.co.uk"
export AWS_SECRET=""
export AWS_KEY=""
export AWS_REGION=""

echo -n "--- Configured for development ---"

#play
 ~/activator run
