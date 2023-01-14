FROM gitpod/workspace-full

RUN brew install scalaenv sbt@0.13
RUN scalaenv install scala-2.10.7 && scalaenv global scala-2.10.7
