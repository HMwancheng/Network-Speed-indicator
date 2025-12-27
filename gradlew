#!/usr/bin/env sh

dirname "$0" | grep -E "^(/[^/]+)+$" > /dev/null || { echo "ERROR: Cannot determine the absolute path of the 'gradlew' script" >&2; exit 1; }

GRADLE_HOME="$(cd "$(dirname "$0")" && pwd)"

if [ -n "$JAVA_HOME" ] ; then
    if [ -x "$JAVA_HOME/jre/sh/java" ] ; then
        # IBM's JDK on AIX uses strange locations for the executables
        JAVACMD="$JAVA_HOME/jre/sh/java"
    else
        JAVACMD="$JAVA_HOME/bin/java"
    fi
    if [ ! -x "$JAVACMD" ] ; then
        die "ERROR: JAVA_HOME is set to an invalid directory: $JAVA_HOME\nPlease set the JAVA_HOME variable in your environment to match the\nlocation of your Java installation."
    fi
else
    JAVACMD="java"
    which java >/dev/null 2>&1 || die "ERROR: JAVA_HOME is not set and no 'java' command could be found in your PATH.\nPlease set the JAVA_HOME variable in your environment to match the\nlocation of your Java installation."
fi

exec "$JAVACMD" "-Dorg.gradle.appname=gradlew" -classpath "$GRADLE_HOME/gradle/wrapper/gradle-wrapper.jar" org.gradle.wrapper.GradleWrapperMain "$@"