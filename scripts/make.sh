#!/bin/bash

./scripts/clean.sh

./scripts/make-jar.sh

mkdir -p out/tmp
mkdir -p out/logs

./scripts/make-scripts.sh

