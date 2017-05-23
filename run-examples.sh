#!/bin/bash

set -e

root=$(realpath $(dirname $0))
reproto=$root/../reproto

if [[ ! -d $reproto ]]; then
    echo "reproto must be checked out in: $reproto"
    exit 1
fi

if ! which reproto 2>&1 > /dev/null; then
    echo "Installing reproto using `cargo install reproto`:"
    cargo install reproto
fi

echo "Installing Plugin"
mvn -q install

for dir in $PWD/examples/*; do
    pushd $dir

    echo "Building: $dir"
    mvn "$@" package
    mvn -q dependency:build-classpath -D mdep.outputFile=target/classpath

    classpath=$(cat target/classpath):target/classes

    echo "Running: $dir"
    java -cp $classpath se.tedro.tests.App

    popd
done

exit 0
