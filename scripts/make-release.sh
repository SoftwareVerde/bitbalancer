#!/bin/bash

echo -n "Version: "
read VERSION

rm -rf release 2>/dev/null
mkdir release

git clone https://github.com/softwareverde/bitbalancer.git release
cd release

git checkout "${VERSION}"

# Build the jar.
./scripts/make.sh

cd out

# Create the tarball.
cd ..
mv out "bitbalancer-${VERSION}"
tar czf "bitbalancer-${VERSION}.tar.gz" "bitbalancer-${VERSION}"
mv "bitbalancer-${VERSION}.tar.gz" ../.


