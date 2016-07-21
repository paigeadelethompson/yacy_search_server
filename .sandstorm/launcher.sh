#!/bin/bash
set -euo pipefail
# This script is run every time an instance of our app - aka grain - starts up.
# This is the entry point for your application both when a grain is first launched
# and when a grain resumes after being previously shut down.
#
# This script is responsible for launching everything your app needs to run.  The
# thing it should do *last* is:
#
#   * Start a process in the foreground listening on port 8000 for HTTP requests.
#
# This is how you indicate to the platform that your application is up and
# ready to receive requests.  Often, this will be something like nginx serving
# static files and reverse proxying for some other dynamic backend service.
#
# Other things you probably want to do in this script include:
#
#   * Building folder structures in /var.  /var is the only non-tmpfs folder
#     mounted read-write in the sandbox, and when a grain is first launched, it
#     will start out empty.  It will persist between runs of the same grain, but
#     be unique per app instance.  That is, two instances of the same app have
#     separate instances of /var.
#   * Preparing a database and running migrations.  As your package changes
#     over time and you release updates, you will need to deal with migrating
#     data from previous schema versions to new ones, since users should not have
#     to think about such things.
#   * Launching other daemons your app needs (e.g. mysqld, redis-server, etc.)

# By default, this script does nothing.  You'll have to modify it as
# appropriate for your application.
cd /opt/app

# Make writable data folder for YaCy
mkdir -p /var/lib/yacy-data

# Make home to the only write enabled directory
export HOME=/var/lib

# Set required environment variables
export JAVA_HOME=/usr/lib/jvm/java-7-openjdk-amd64 
export LD_LIBRARY_PATH="$JAVA_HOME/jre/lib:$JAVA_HOME/jre/lib/amd64:$JAVA_HOME/jre/lib/amd64/jli:$JAVA_HOME/jre/lib/server" 

export PATH="$PATH:$JAVA_HOME"

# export _JAVA_OPTIONS="-Xms64m -Xmx512m -XX:MaxMetaspaceSize=64m -XX:-UseLargePages -Xss256k"

# First, check java is working inside grain
java -Xms64m -Xmx512m -version

# Launch YaCy main process from its data folder and pass application folder as parameter
# TODO initialize here YaCy port to 8000 (Sandstorm waits for this), as done on Heroku branch
java -Xms64m -Xmx512m -Djava.awt.headless=true -Dfile.encoding=UTF-8 -classpath classes:lib/* net.yacy.yacy -start yacy-data
