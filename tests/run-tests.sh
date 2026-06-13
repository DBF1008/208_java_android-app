#!/usr/bin/env bash
#
# Compiles and runs the user-center regression test with a bare JDK.
#
# The app is a legacy Eclipse/ADT project with no Gradle and no Android SDK
# checked in, so an instrumentation / Robolectric test cannot be built here.
# The user-center state-restoration fix relies on the UserResponse object graph
# being Serializable and round-tripping losslessly (that is what a Bundle does to
# the fragment arguments across a configuration change / process death). The
# entity classes have no Android dependency, so that contract is verified with
# plain Java serialization on any JDK.
#
set -euo pipefail

HERE="$(cd "$(dirname "$0")" && pwd)"
SRC="$HERE/../source/src"
ENTITY="$SRC/cn/eoe/app/entity"
OUT="$(mktemp -d)"
trap 'rm -rf "$OUT"' EXIT

echo "Compiling entities + test (javac $(javac -version 2>&1))..."
javac -encoding UTF-8 -d "$OUT" \
	"$ENTITY/UserResponse.java" \
	"$ENTITY/UserInfoItem.java" \
	"$ENTITY/UserIcon.java" \
	"$ENTITY/UserFavoriteList.java" \
	"$ENTITY/UserCollectionItem.java" \
	"$HERE/cn/eoe/app/entity/UserResponseStateRestorationTest.java"

echo "Running UserResponseStateRestorationTest..."
java -Dfile.encoding=UTF-8 -cp "$OUT" \
	cn.eoe.app.entity.UserResponseStateRestorationTest
