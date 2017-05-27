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

for dir in tests/*; do
    pushd $dir
    echo "Testing: $dir"
    mvn -q "$@" clean test
    popd
done

exit 0
