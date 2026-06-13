#!/bin/sh
# Compiles and runs the pure-Java unit tests for the search "latest input wins"
# fix (SearchActivity / SearchRequestCoordinator).
#
# These tests deliberately have NO Android dependency, so any JDK is enough to
# run them -- the Android SDK is not required:
#
#     sh source/run_unit_tests.sh
#
set -e

HERE="$(cd "$(dirname "$0")" && pwd)"
OUT="$HERE/build/test-classes"
CP="$HERE/testlibs/junit-4.12.jar:$HERE/testlibs/hamcrest-core-1.3.jar"

rm -rf "$OUT"
mkdir -p "$OUT"

javac -encoding UTF-8 -d "$OUT" -cp "$CP" \
    "$HERE/src/cn/eoe/app/utils/SearchRequestCoordinator.java" \
    "$HERE/test/cn/eoe/app/utils/SearchRequestCoordinatorTest.java"

java -cp "$OUT:$CP" org.junit.runner.JUnitCore \
    cn.eoe.app.utils.SearchRequestCoordinatorTest
