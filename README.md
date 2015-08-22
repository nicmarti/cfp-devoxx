# Call for Papers application

The CFP is a Call for Papers application for Conferences. 

The application allows a speaker to register with Github/Google+ or LinkedIn, then post one or more proposals for a Conference.

A program committee can then vote, ask questions to speaker and finally, build an Agenda for a conference.
The CFP offers also a REST API with the list of selected talks, speakers and the schedules. 

In 2015, the Devoxx France's CFP received 681 proposals for 220 slots. The 16 members of the technical committee for Devoxx FR did more than 6700 reviews in 2 months.

# Contributors

Original author: Nicolas Martignole [@nmartignole](http://www.twitter.com/nmartignole)

- Gabriel Kastenbaum [@lambdadevfr](http://www.twitter.com/lambdadevfr)
- Jean Helou [@jeanhelou](http://www.twitter.com/jeanhelou) 
- Frédéric Camblor [@fcamblor](http://www.twitter.com/fcamblor)
- Nicolas de Loof [@ndeloof](http://www.twitter.com/ndeloof)

## License

The CFP application is licensed under the MIT License. See License.txt or [http://opensource.org/licenses/MIT](http://opensource.org/licenses/MIT)

Copyright (c) 2013 Association du Paris Java User Group & [Nicolas Martignole](http://www.touilleur-express.fr/).

## Background

The CFP was originally created in 2013 for the [Devoxx France](http://www.devoxx.fr/) 2014 conférence. Devoxx France is the 2nd biggest conference for dev in France with 2500 attendees in 2015.
The conference has top sponsors like Google, Oracle, IBM and Microsoft. The conference was created by Nicolas Martignole, Antonio Goncalves and Zouheir Cadi.
 
The CFO is implemented with Scala, with Play Framework v2.2.3. It uses Redis 2.8 to persist data. Elastic Search is used for better user experience.
 
## Which Conferences are using it?

- [Devoxx France](http://www.devoxx.fr)
- [Devoxx Belgium](http://www.devoxx.be)
- [Devoxx Poland](http://www.devoxx.pl)
- [Devoxx UK](http://www.devoxx.co.uk)
- [Devoxx Morocco](http://www.devoxx.ma)
- [Web2Day Tech2Day](http://cfp.web2day.co/)
- [BDX.IO](http://cfp.bdx.io/)
- [Scala.IO](http://cfp.scala.io/)
- [Breizh Camp](http://cfp.breizhcamp.org/)
- [Droid Con](http://cfp.droidcon.fr/home/)

Send a message to (@nmartignole)[http://www.twitter.com/nmartignole) if you plan to use the CFP.

## How to set-up a local and friendly developer environment ?

- Install Play 2.2.3 (not the latest version with activator)
- Install Redis 2.8.4, do not use "brew install redis" on Mac, as it would install 2.6, an older version of Redis
- Read Redis documentation and learn Redis with http://try.redis.io
- Read also the self-document redis.conf https://raw.githubusercontent.com/antirez/redis/2.8/redis.conf

Optional but recommended for better user experience:

- Install ElasticSearch
- Create Github App and configure OAuth. See [the Github site](https://github.com/settings/applications) 
- Create an application using your [Google account](https://cloud.google.com/console#/project). Configure a URL for development, such as http://localhost:9000/ and prod URL as http://cfp.devoxx.fr/
- Create a LinkedIn App and configure OAuth
- a [Mailjet](http://www.mailjet.com) account for SMTP sending transactional emails 

## Here's what you need to configure:

- Rename the application-please-customize-me.conf file to application.conf
- Generate a string for the security of the application 
   application.secret = "a_unique_secret_long_enough"
- As the application uses play.api.libs.Crypto#encryptAES, this secret MUST be at least 16 chars long.
- Configure the SMTP server using the parameters Mailjet OR use the smtp.mock mode in DEV
- Configure the Github part
- Set the Google party for authentication OAuth2.0
- configure the Redis server. Make sure to set a very long password for your Redis server
- configure the address of a server ElasticSearch


## Where do I start?

First things first, you need to set-up your own conference. To do so, Frederic Camblor implemented a generic
class that contains most (but not all) importants details. Check ConferenceDescriptor.scala. This file defines
the configuration of your own conference. The Schedule/Slots is not mandatory when you start to configure your application. 
However if you plan to use the REST API then you should also configure this part. Check for the TODO's in the file.

You can then also translate and check messages/messages.fr from the conf file

## How can I create a new user?

To create an admin:
  - Start the CFP with a local Redis server
  - Create a new User (http://localhost:9000/home) with the Registration system
  - If you configured smtp.mock="yes" in application.conf, check the console. You should see the "please validate your email" message
  - Loads and validate your user
  - Once authenticated, retrieve your UUID from the "Edit my Profile" page (/cfp/profile)
  - Load the bootstrap URL http://localhost:9000/admin/bootstrapAdminUser?uuid=[your_uuid]. Please note that if there is already an admin, this won't work.
  
You can also add an existing user to the Admin group directly from Redis console :
   

## In term of Git, how can I push a new feature?

The main development branch is dev. It' an out-of-the-box ready to use conference. This is also where I try to collect all features from all contributors.

I created a branch dev-france for current development regarding Devoxx France. I did the same for BE.
I work with one feature per branch, then do local merge.

When you want to update your local branch (for instance, dev-poland) you should :

    - do a checkout of dev
    - do a git pull --rebase on dev
    - switch to dev-poland
    - do a git pull --rebase from dev to dev-poland so that you keep your local updates


## How can I save my agenda and host it to a Wordpress ?
  
Use WGET and download all pages from your Publisher controller. This will save speakers, talks, schedule, etc.

```wget --no-clobber --convert-links -r -p -E -e robots=off http://localhost:9000/2014```

## Can you help me with Redis 2.8.x ?


Downloading redis...tag.gz from http://download.redis.io/releases/redis-2.8.19.tar.gz

Unpack the archive

    $ make 
    $ make install

Create a custom redis configuration file. Be sure to set a very strong password. Redis is written in C and is mono-core.
On my super Intel i7 it runs on one Core. Thus it's ok to have multiple Redis on differents ports. 

How to run the redis server with custom config file ?

    $ redis-server [xxx.conf-file]

Note: ensure all paths in the .conf file exists otherwise, use touch to create those files / paths

How to run the redis client ?

Once the redis-server is up and running, do the following:

$ redis-cli -p 6366

Some commands to remember:
	
	> INFO
	> DBSIZE
    > SYNC - helps sync remote server with local server (cluster)
	> MONITOR
	> SMEMBERS Webuser:admin
	> SADD Webuser:admin [sha1]
	
	
Once running on a local empty Redis, you will want to have a local user with admin privileges in the application.

- Create a user and activate it
- Play log will give you the activation link if running on smtp.mock=”yes”
- Connect to the Redis instance
- Find the UUID of the user you want to be admin
- The command keys Webuser:UUID* will list all known user UUIDs
- Find which one is your soon to be admin by running get Webuser:UUID:<UUID> and the output gives you the email.
- Add the UUID to admin and cfp groups using redis-cli (the redis CLI). 

    > SADD Webuser:admin <UUID> 
    > SADD Webuser:cfp <UUID>

If you want to promote an existing user to admin on your PROD server, you can also use redis-cli to connect to the remote server
Let's say you want to add John with ID=UUID_123456 to the remote PROD redis-prod.mydomain.com that is running on port 6393. The Master 
password on this remote server is "my_super_password_for_prod". 

    nicolas@macbook :~/Dev/DevoxxFR/2014/RedisBackup> redis-cli -h redis-prod.mydomain.com -p 6393
    redis-prod.mydomain.com:6393> info
    NOAUTH Authentication required.
    redis-prod.mydomain.com:6393> AUTH my_super_password_for_prod
    OK
    redis-prod.mydomain.com:6393> SADD Webuser:admin UUID_123456
    redis-prod.mydomain.com:6393> SADD Webuser:cfp UUID_123456
    ...


- Restart the application to clear its caches (or use /admin/clearCaches if you are already an admin and connected)


## Redis is an in-memory server... How can I be sure that I won't loose my data ?
	
First, read the Redis documentation. I use AOF and BGSAVE on my production servers. I also use Linux Dropbox client so that I can save some dump automatically. 
I have also configured my personal computer to be a slave of all my Redis servers. This is very practical, you get a live copy of Redis on your laptop. 
	
See redis-sample-dev.conf and redis-sample-prod.conf for 2 valid configuration files for Redis.	
	
Just to give you an idea and some stats for my Devoxx France Redis database :
 
    - Number of Speakers : 946
    - Number of Proposals : 681 
    - Number of Reviews : 6704
    - Redis DB Size : 8388
    - DUMP file size : 65M
    - AOF file size : 73M
    - Number of lines in the AOF file : 2,344,818
    - Memory used by Redis : 142 Mo

## Where do you host your CFP for Devoxx France?
  
For Devoxx France, we use [Clever-Cloud](http://www.clever-cloud). Clever Cloud is a Platform as a Service. Git push and voilà, your code is deployed.
Redis and ElasticSearch are on a dedicated server.
  
## Can I contribute?

Yes, you can contribute. Please, create Pull-Requests from the DEV branches. Try to create a Pull-request per feature, write clean code.
If the proposed PR is too complex or too specific to a Conference, we will not merge it to the main source tree.

Merci

Nicolas Martignole
