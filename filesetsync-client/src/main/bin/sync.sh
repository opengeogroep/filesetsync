#!/bin/bash

# Directory layout:
# bin/sync.sh
# bin/sync.cmd
# bin/filesetsync-client.jar
# bin/filesetsync-config.xml
# bin/dependency/*.jar
# var/

# Configure a fileset (with delete=true) to sync updated client bin
# to the "update/" dir with the property:
# <property name="exitAfterUpdate" value="123"/>
# sync tool will exit if client is updated with the exit code and
# move bin/ to bak/ and copy release/ to bin/ and start the
# updated client. This will always restart the client the first time
# with the update/ directory empty.

BASEPATH=`realpath $(dirname $0)`
cd $BASEPATH/..
echo "In directory $PWD running sync tool"

java -jar bin/filesetsync-client-*.jar

while [ $? -eq 123 ]; do
  # update bin dir because of special exit code
  echo "Updating client"
  rm -rf bak 2>/dev/null
  mv bin bak
  cp -r update bin
  echo "Running client again"
  java -jar bin/filesetsync-client-*.jar
done

