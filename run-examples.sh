#!/bin/bash

set -e

root=$(realpath $(dirname $0))
reproto=$root/../reproto

if [[ ! -d $reproto ]]; then
    echo "reproto must be checked out in: $reproto"
    exit 1
fi

(cd $reproto && cargo build --release)

exe=$(realpath $reproto/target/release/reproto)

if [[ ! -x $exe ]]; then
    echo "Not an executable: $exe"
    exit 1
fi

echo "Installing Plugin"
mvn -q install

for dir in $PWD/examples/*; do
    pushd $dir

    echo "Building: $dir"
    mvn -q clean package -D reproto.executable=$exe
    mvn -q dependency:build-classpath -D mdep.outputFile=target/classpath

    classpath=$(cat target/classpath):target/classes

    echo "Running: $dir"
    java -cp $classpath se.tedro.tests.App

    popd
done

exit 0
