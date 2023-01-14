FROM gitpod/workspace-full:2023-01-10-16-35-53

SHELL ["/bin/bash", "-c"]

RUN bash -c "source /home/gitpod/.sdkman/bin/sdkman-init.sh && \
   sdk install java 8.0.352-zulu < /dev/null && \
   sdk default java 8.0.352-zulu && \
   sdk uninstall java 17.0.5.fx-zulu && \
   sdk uninstall java 11.0.17.fx-zulu"

ENV IDEA_JDK /home/gitpod/.sdkman/candidates/java/current
ENV JAVA_HOME /home/gitpod/.sdkman/candidates/java/current

# Let's install sbt dependencies
RUN brew install sbt@0.13
RUN source "/home/gitpod/.sdkman/bin/sdkman-init.sh"  \
    && /home/linuxbrew/.linuxbrew/opt/sbt@0.13/bin/sbt --version

# Installing Scala
RUN brew install scalaenv
RUN scalaenv install scala-2.11.12 && scalaenv global scala-2.11.12

# Persisting bash variables
RUN echo 'source "/home/gitpod/.sdkman/bin/sdkman-init.sh" >> $HOME/.bashrc'
RUN echo 'export PATH="${HOME}/.scalaenv/bin:${PATH}"' >> $HOME/.bashrc
RUN echo 'eval "$(scalaenv init -)"' >> $HOME/.bashrc

# Making a first run so that everything needed is provisionned
COPY ./ bootstrapping-project/cfp-devoxx/

WORKDIR /home/gitpod/bootstrapping-project/cfp-devoxx/

# I don't know why, but if I try to sbt compile "directly", docker build hangs forever
# whilst splitting it in 3 steps makes it build it properly
RUN date \
    && source "/home/gitpod/.sdkman/bin/sdkman-init.sh" \
    && export SBT_OPTS="-Xms4096M -Xmx4096M -Xss1M -XX:+CMSClassUnloadingEnabled -XX:MaxMetaspaceSize=1024M -XX:MaxPermSize=1024M" \
    && ./.gitpod/show-elapsed-time.sh '/home/linuxbrew/.linuxbrew/opt/sbt@0.13/bin/sbt --verbose -version'
RUN date \
    && source "/home/gitpod/.sdkman/bin/sdkman-init.sh" \
    && export SBT_OPTS="-Xms4096M -Xmx4096M -Xss1M -XX:+CMSClassUnloadingEnabled -XX:MaxMetaspaceSize=1024M -XX:MaxPermSize=1024M" \
    && ./.gitpod/show-elapsed-time.sh '/home/linuxbrew/.linuxbrew/opt/sbt@0.13/bin/sbt --verbose update'
RUN date \
    && source "/home/gitpod/.sdkman/bin/sdkman-init.sh"  \
    && export SBT_OPTS="-Xms4096M -Xmx4096M -Xss1M -XX:+CMSClassUnloadingEnabled -XX:MaxMetaspaceSize=1024M -XX:MaxPermSize=1024M" \
    && ./.gitpod/show-elapsed-time.sh '/home/linuxbrew/.linuxbrew/opt/sbt@0.13/bin/sbt --verbose compile'

WORKDIR /home/gitpod
COPY ./.gitpod/run.sh ./

# Removing useless stuff, except than target directories
# RUN find bootstrapping-project/cfp-devoxx ! -iregex '(target|project/target)' | xargs rm -f
