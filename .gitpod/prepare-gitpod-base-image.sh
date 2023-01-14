#!/bin/sh -x

# Mandatory, should look like 'fcamblor/cfp-devoxx:0.6'
# (it's the name+tag of the docker image you want to build and publish)
docker_tagname=$1
# It's the temporary dirname you want to use. If left empty, a timestamp directory will be created
# Pass an existing tmp directory value here if you want to resume a previous docker build
tmp_dirname=${2:-''}
# The git repository which is going to be cloned if tmp_dirname above doesn't exist
repo=${3:-git@github.com:nicmarti/cfp-devoxx.git}

if [ -z "$tmp_dirname" ]; then
  rootdir="/tmp/`date +'%Y-%m-%dT%H_%M_%S'`"
else
  rootdir="/tmp/$tmp_dirname"
fi

if [ -d "$rootdir" ]; then
  cd $rootdir
else
  sha1=`git rev-parse --short HEAD`

  mkdir $rootdir

  cd $rootdir
  git clone "$repo" .
  git checkout "$sha1"
fi

echo `pwd`

docker build --progress=plain -m 4g -t "$docker_tagname" -f ./.gitpod/base.Dockerfile .
docker push "$docker_tagname"
