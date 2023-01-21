# Purpose

If you look at `gitpod.yml` file at the root of the repository, you will notice gitpod relies on a custom docker image.

This custom image brings following benefits :  
- JDK 8, Scala 2.11 & SBT 0.13 pre-installed
- A first `sbt compile` being executed, in order to already have most of the required dependencies to run  
  the CFP app
- It makes the first `sbt run` executed from the gitpod instance very fast

Script `prepare-gitpod-base-image.sh` is intended to build a new version of this gitpod image.

The idea of this script is to :  
- Clone the git repository in a temp directory
- Checkout the sha1 you're presently on in your current workspace (**it means that this sha1 needs to be pushed !**)
- Build the image using `base.Dockerfile` file with all installations above
- Create a new docker image tag and push it in dockerhub

It should be called like this:
```
# The first time you call it
./.gitpod/prepare-gitpod-base-image.sh 'fcamblor/cfp-devoxx:0.6'

# If you want to resume a previous docker build, note the tmp directory timestamp name and provide it as second param
./.gitpod/prepare-gitpod-base-image.sh 'fcamblor/cfp-devoxx:0.6' '2023-01-14T17_11_02' 
```

Beware: during build of `base.Dockerfile`, build may hang up forever, particularly during the `sbt` command calls.
That's one of the reasons why multiple (progressive) `sbt` are run, in order to take benefit from docker build cache
when trying to build the image multiple times in a row (if docker build is killed due to forever hanging)

In order to help you determine if a step taking a long time is "normal" or not, I provided an example log file
(see `example-sbt-compile-logs.log`) where you will see duration for every working steps.

Typically, in this file, we can see something like this :
```
#16 [12/16] RUN date     && source "/home/gitpod/.sdkman/bin/sdkman-init.sh"     && export SBT_OPTS="-Xms4096M -Xmx4096M -Xss1M -XX:+CMSClassUnloadingEnabled -XX:MaxMetaspaceSize=1024M -XX:MaxPermSize=1024M"     && ./.gitpod/show-elapsed-time.sh '/home/linuxbrew/.linuxbrew/opt/sbt@0.13/bin/sbt --verbose -version'
#16 sha256:d7afdcf854c7ccf613fb0815f2042abff2cfc5e5fc19a07a321390a510bb711d
#16 0.332 Fri 13 Jan 2023 04:07:52 PM UTC
Elapsed time: 1 secs OpenJDK 64-Bit Server VM warning: ignoring option MaxPermSize=1024M; support was removed in 8.0
Elapsed time: 37 secs [info] Loading project definition from /home/gitpod/.sbt/0.13/staging/d91d15f4d3b8d7a8a9e6/cfp-devoxx/project
```

It means that, if during your execution, you've seen the line:
```OpenJDK 64-Bit Server VM warning:...``` 
but you're not seeing the line:
```[info] Loading project definition...```
after, let's say, 50s, then it means that your current build is going to hang forever  
_(you will have to kill it and resume building your image)_

Once your done building and pushing your image, simply update `.gitpod.yml` file with this new image and
push your commit so that it is picked during your next gitpod workspace initialization.
