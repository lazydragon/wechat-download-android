#!/bin/sh
APP_NAME="Gradle"
APP_BASE_NAME=${0##*/}
app_path=$0
while [ -h "$app_path" ]; do
    ls=$( ls -ld -- "$app_path" )
    link=${ls#*' -> '}
    case $link in
      /*) app_path=$link ;;
      *)  app_path=${app_path%/*}/$link ;;
    esac
done
APP_HOME=$( cd "${app_path%/*}/" > /dev/null && pwd -P ) || exit
CLASSPATH=$APP_HOME/gradle/wrapper/gradle-wrapper.jar
if [ -n "$JAVA_HOME" ]; then
    if [ -x "$JAVA_HOME/jre/sh/java" ]; then
        JAVACMD=$JAVA_HOME/jre/sh/java
    else
        JAVACMD=$JAVA_HOME/bin/java
    fi
    if [ ! -x "$JAVACMD" ]; then
        echo "ERROR: JAVA_HOME is set to an invalid directory: $JAVA_HOME" >&2
        exit 1
    fi
else
    JAVACMD=java
    if ! command -v java > /dev/null 2>&1; then
        echo "ERROR: JAVA_HOME is not set and no 'java' command found in PATH." >&2
        exit 1
    fi
fi
DEFAULT_JVM_OPTS="-Xmx64m -Xms64m"
exec "$JAVACMD" \
    $DEFAULT_JVM_OPTS $JAVA_OPTS $GRADLE_OPTS \
    "-Dorg.gradle.appname=$APP_BASE_NAME" \
    -classpath "$CLASSPATH" \
    org.gradle.wrapper.GradleWrapperMain \
    "$@"
