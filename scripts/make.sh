#!/bin/bash

./scripts/clean.sh

./scripts/make-jar.sh

mkdir -p out/tmp
mkdir -p out/logs

./scripts/make-scripts.sh

mkdir -p out/conf
cp conf/server.json out/conf/.

