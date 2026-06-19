#!/bin/sh
# Maven Wrapper script
# Downloads and runs Maven if not already available

set -e

MAVEN_PROJECTBASEDIR="${MAVEN_BASEDIR:-$(cd "$(dirname "$0")" && pwd)}"
MAVEN_WRAPPER_JAR="$MAVEN_PROJECTBASEDIR/.mvn/wrapper/maven-wrapper.jar"
MAVEN_WRAPPER_PROPERTIES="$MAVEN_PROJECTBASEDIR/.mvn/wrapper/maven-wrapper.properties"

if [ -f "$MAVEN_WRAPPER_PROPERTIES" ]; then
    MAVEN_DIST_URL=$(grep "distributionUrl" "$MAVEN_WRAPPER_PROPERTIES" | cut -d'=' -f2-)
fi

MAVEN_HOME="$HOME/.m2/wrapper/dists/apache-maven-3.9.6"

if [ ! -d "$MAVEN_HOME" ]; then
    echo "Downloading Maven..."
    mkdir -p "$MAVEN_HOME"
    TMPFILE=$(mktemp)
    curl -sL "$MAVEN_DIST_URL" -o "$TMPFILE"
    unzip -qo "$TMPFILE" -d "$HOME/.m2/wrapper/dists/"
    rm -f "$TMPFILE"
fi

MAVEN_BIN=$(find "$HOME/.m2/wrapper/dists/" -name "mvn" -path "*/bin/mvn" | head -1)
if [ -z "$MAVEN_BIN" ]; then
    echo "ERROR: Could not find Maven executable"
    exit 1
fi

exec "$MAVEN_BIN" "$@"
