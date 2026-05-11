#!/bin/sh
# Gradle start-up script for POSIX systems. Bootstraps gradle-wrapper.jar which
# downloads the distribution declared in gradle/wrapper/gradle-wrapper.properties.
set -e
APP_BASE_NAME=${0##*/}
APP_HOME=$(cd "$(dirname "$0")" && pwd -P)
DEFAULT_JVM_OPTS='"-Xmx64m" "-Xms64m"'

if [ -n "$JAVA_HOME" ] ; then
    if [ -x "$JAVA_HOME/jre/sh/java" ] ; then
        JAVACMD="$JAVA_HOME/jre/sh/java"
    else
        JAVACMD="$JAVA_HOME/bin/java"
    fi
    [ -x "$JAVACMD" ] || { echo "ERROR: JAVA_HOME=$JAVA_HOME invalid" >&2; exit 1; }
else
    JAVACMD=java
    command -v java >/dev/null 2>&1 || { echo "ERROR: JAVA_HOME not set and 'java' not on PATH" >&2; exit 1; }
fi

CLASSPATH=$APP_HOME/gradle/wrapper/gradle-wrapper.jar
exec "$JAVACMD" $DEFAULT_JVM_OPTS $JAVA_OPTS $GRADLE_OPTS \
    "-Dorg.gradle.appname=$APP_BASE_NAME" \
    -classpath "$CLASSPATH" \
    org.gradle.wrapper.GradleWrapperMain "$@"
