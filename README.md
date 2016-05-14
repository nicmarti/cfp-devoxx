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
- Mani Sarkar [theNeomatrix369](http://www.twitter.com/theNeomatrix369)

## License

The CFP application is licensed under the MIT License. See License.txt or [http://opensource.org/licenses/MIT](http://opensource.org/licenses/MIT)

Copyright (c) 2013 Association du Paris Java User Group & [Nicolas Martignole](http://www.touilleur-express.fr/).

## Background

The CFP was originally created in 2013 for the [Devoxx France](http://www.devoxx.fr/) 2014 edition. Devoxx France is one of the biggest conference for Developers in France with 2500 attendees in 2015.
The conference had top sponsors like Google, Oracle, IBM and Microsoft. The conference is organized by Nicolas Martignole, Antonio Goncalvès and Zouheir Cadi.
 
The CFP is implemented with Scala and Play Framework v2.2.3. Redis 2.8 is used for persistence. Elastic Search is integrated as a search engine and to calculate stats with Facets.
 
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
- Install Redis 2.8.21 (or better, but NOT Redis 3.x), do not use "brew install redis" on Mac, as it would install 2.6, an older version of Redis
- Read Redis documentation and learn Redis with http://try.redis.io
- Read also the self-document redis.conf https://raw.githubusercontent.com/antirez/redis/2.8/redis.conf 

Optional but recommended for better user experience:

- Install ElasticSearch (1.2.0 or better) This version uses Facets.
- Create Github App and configure OAuth. See [the Github site](https://github.com/settings/applications) 
- Create an application using your [Google account](https://cloud.google.com/console#/project). Configure a URL for development, such as http://localhost:9000/ and prod URL as http://cfp.devoxx.fr/
- Create a LinkedIn App and configure OAuth
- a [Mailjet](http://www.mailjet.com) account for SMTP sending transactional emails 

## I'm using Docker and Docker-Machine

A docker compose file is provided with Redis and Elastic Search configuration.

To connect to your local Redis Cli, you can use this command line as an example :

```docker run -it --link cfpdevoxx_redis_1:redis --rm redis sh -c 'exec redis-cli -h "$REDIS_PORT_6379_TCP_ADDR" -p "$REDIS_PORT_6379_TCP_PORT"'``

## Here's what you need to configure:

- Rename the run.sh.sample file to run.sh
- Generate a string for the security of the application 
   application.secret = "a_unique_secret_long_enough"
- As the application uses play.api.libs.Crypto#encryptAES, this secret MUST be at least 16 chars long.
- Configure the SMTP server using the parameters Mailjet OR use the smtp.mock mode in DEV
- Configure the Github part
- Set the Google party for authentication OAuth2.0
- Configure also LinkedIn
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


## How can I save my agenda and host the program as static content to a Wordpress ?
  
Use WGET and download all pages from your Publisher controller. This will save speakers, talks, schedule, etc.

```wget --no-clobber --convert-links -r -p -E -e robots=off http://localhost:9000/2016/index.html```

## Can you help me with Redis 2.8.x ?

Downloading redis...tag.gz from http://download.redis.io/releases/redis-2.8.21.tar.gz

The CFP has been tested with Redis from version 2.8.4 to 2.8.19. Always check that your version is up-to-date in term
of security [here](https://raw.githubusercontent.com/antirez/redis/2.8/00-RELEASENOTES). I plan to upgrade and to check 
that the CFP code is OK with Redis 3.x before Devoxx France 2016.

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
	
Just to give you an idea and some stats for our Devoxx France 2015 Redis database :
 
    - Number of Speakers : 946
    - Number of Proposals : 681 
    - Number of Reviews : 6704
    - Redis DB Size : 8388
    - DUMP file size : 65M
    - AOF file size : 73M
    - Number of lines in the AOF file : 2,344,818
    - Memory used by Redis : 142 Mo

## Where do you host your CFP for Devoxx France?
  
The Devoxx France CFP is hosted on [Clever-Cloud](http://www.clever-cloud). Clever Cloud is a Platform as a Service. Git push and voilà, your code is deployed.
Redis and ElasticSearch are on a dedicated server.

## Why do you use Play 2.2.x and not the latest version?

I recommend [Play 2.2.6](https://downloads.typesafe.com/play/2.2.6/play-2.2.6.zip). I have a strong experience with Play since
2011 and Play 1.x. I did more than 20 presentations of Play! Framework since 2010. 

I plan to evaluate the need to migrate to Play Framework 2.4. But I'm not a super-fan with this version and with what the core developers decided to do since early 2015.
  
## Contributing

**[Pull requests](https://github.com/git-up/GitUp/pulls) are welcome but be aware that the CFP is really focus on Devoxx conferences.**

The following is a list of absolute requirements for PRs (not following them would result in immediate rejection):
- You MUST use space for indentation instead of tabs
- The coding style MUST be followed exactly (default IDEA IntelliJ 14 settings)
- Each commit MUST be a single change (e.g. adding a function or fixing a bug, but not both at once)
- Each commit MAY respect the Commit log convention (see below)
- The pull request MUST contain as few commits as needed
- The pull request MUST NOT contain fixup or revert commits (flatten them beforehand using GitUp!)
- The pull request MUST be rebased on latest `dev` when sent

## <a name="commit"></a> Git Commit Guidelines (from AngularJS source code)

We have very precise rules over how our git commit messages can be formatted.  This leads to **more readable messages** that are easy to follow when looking through the **project history**. 

### Commit Message Format
Each commit message consists of a **header**, a **body** and a **footer**.  The header has a special
format that includes a **type**, a **scope** and a **subject**:

```
<type>(<scope>): <subject>
<BLANK LINE>
<body>
<BLANK LINE>
<footer>
```

The **header** is mandatory and the **scope** of the header is optional.

Any line of the commit message cannot be longer 100 characters! This allows the message to be easier to read on GitHub as well as in various git tools.

### Revert
If the commit reverts a previous commit, it should begin with `revert: `, followed by the header of the reverted commit. In the body it should say: `This reverts commit <hash>.`, where the hash is the SHA of the commit being reverted.

### Type
Must be one of the following:

* **feat**: A new feature
* **fix**: A bug fix
* **docs**: Documentation only changes
* **style**: Changes that do not affect the meaning of the code (white-space, formatting, missing
  semi-colons, etc)
* **refactor**: A code change that neither fixes a bug nor adds a feature
* **perf**: A code change that improves performance
* **test**: Adding missing tests
* **chore**: Changes to the build process or auxiliary tools and libraries such as documentation
  generation

### Scope
The scope could be anything specifying place of the commit change, usually related to a Play Controller. For example `admin`,
`api`, `publisher`, etc.

### Subject
The subject contains succinct description of the change:

* use the imperative, present tense: "change" not "changed" nor "changes"
* don't capitalize first letter
* no dot (.) at the end

### Body
Just as in the **subject**, use the imperative, present tense: "change" not "changed" nor "changes".
The body should include the motivation for the change and contrast this with previous behavior.

### Footer
The footer should contain any information about **Breaking Changes** and is also the place to
reference GitHub issues that this commit **Closes**.

**Breaking Changes** should start with the word `BREAKING CHANGE:` with a space or two newlines. The rest of the commit message is then used for this.

A detailed explanation can be found in the [AngularJS Git commit documentation](https://docs.google.com/document/d/1QrDFcIiPjSLDn3EL15IJygNPiHORgU1_OOAqWjiDU5Y/edit).

Merci

Nicolas Martignole
