#!/bin/sh

LOG_DIR=${DRUID_LOGDIR:-/var/log/druid}
CONF_DIR=${DRUID_CONFDIR:-conf/druid}
LIB_DIR=${DRUID_LIB_DIR:-lib}
DRUID_MAIN_CLASS=${DRUID_MAIN_CLASS:-$DRUID_NODE_TYPE}
JAVA=java
if [ "$JAVA_HOME" != "" ]; then
  JAVA=$JAVA_HOME/bin/java
fi


if [ -z $DRUID_NODE_TYPE ]; then
    echo "DRUID_NODE_TYPE not set. Exit" >&2
    exit 1
fi

CLASSPATH="$CONF_DIR/_common:$CONF_DIR/$DRUID_NODE_TYPE:$LIB_DIR/*"
JAVA_OPTS=`cat $CONF_DIR/$DRUID_NODE_TYPE/jvm.config | xargs`

exec ${JAVA} ${JAVA_OPTS} -cp $CLASSPATH org.apache.druid.cli.Main server $DRUID_MAIN_CLASS
