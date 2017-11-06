#!/bin/bash

set -e

MVN=${MVN:-mvn -q}

root=$(realpath $(dirname $0))

echo "Installing Plugin"
$MVN versions:set -DnewVersion=0.0.1-SNAPSHOT
$MVN install
$MVN versions:revert

for dir in tests/*; do
    pushd $dir
    echo "Testing: $dir"
    $MVN "$@" clean test
    popd
done

exit 0
