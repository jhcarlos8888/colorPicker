#!/bin/sh
# Launcher of ColorPicker (@PACKAGE@ package): runs the application jar with
# the first Java runtime found that is version 17 or newer and can open windows.

JAR=@JAR@

headless=
for JAVA in ${JAVA_HOME:+"$JAVA_HOME/bin/java"} \
        /usr/lib/jvm/default-java/bin/java \
        "$(command -v java)" \
        /usr/lib/jvm/java-25-openjdk-*/bin/java \
        /usr/lib/jvm/java-21-openjdk-*/bin/java \
        /usr/lib/jvm/java-17-openjdk-*/bin/java; do
    [ -x "$JAVA" ] || continue
    info=$("$JAVA" -XshowSettings:properties -version 2>&1)
    major=$(printf '%s\n' "$info" | sed -n 's/.* version "\([0-9][0-9]*\).*/\1/p' | head -n 1)
    if [ -n "$major" ] && [ "$major" -ge 17 ]; then
        # A headless runtime (openjdk-NN-jre-headless) has no X11 support.
        home=$(printf '%s\n' "$info" | sed -n 's/^ *java\.home = //p' | head -n 1)
        if [ -n "$home" ] && [ -e "$home/lib/libawt_xawt.so" ]; then
            exec "$JAVA" @JAVA_OPTIONS@ -jar "$JAR" "$@"
        fi
        headless=$JAVA
    fi
done

if [ -n "$headless" ]; then
    echo "@COMMAND@: needs a Java runtime with graphics support, $headless is headless" \
        "(sudo apt install openjdk-17-jre)." >&2
else
    echo "@COMMAND@: Java 17 or newer is required (sudo apt install openjdk-17-jre)." >&2
fi
exit 1
