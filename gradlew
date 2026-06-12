#!/bin/sh
#
# Gradle start up script for UNIX
#

# Determine the Java command to use to start the JVM.
if [ -n "$JAVA_HOME" ] ; then
    if [ -x "$JAVA_HOME/jre/sh/java" ] ; then
        JAVACMD="$JAVA_HOME/jre/sh/java"
    else
        JAVACMD="$JAVA_HOME/bin/java"
    fi
    if [ ! -x "$JAVACMD" ] ; then
        die "ERROR: JAVA_HOME is set to an invalid directory: $JAVA_HOME"
    fi
else
    JAVACMD="java"
    which java >/dev/null 2>&1 || die "ERROR: JAVA_HOME is not set and no 'java' command could be found."
fi

# Collect all arguments for the java command, stacking in reverse order:
#   * args from the command line
#   * the main class name
#   * -classpath
#   * -D...appname settings
#   * --module-path (only if needed)
#   * DEFAULT_JVM_OPTS, JAVA_OPTS, GRADLE_OPTS environment variables.

APP_HOME="$(cd "$(dirname "$0")" && pwd -P)"
APP_NAME="Gradle"
APP_BASE_NAME="$(basename "$0")"

GRADLE_WRAPPER_JAR="$APP_HOME/gradle/wrapper/gradle-wrapper.jar"

exec "$JAVACMD" \
  -classpath "$GRADLE_WRAPPER_JAR" \
  org.gradle.wrapper.GradleWrapperMain "$@"
 
