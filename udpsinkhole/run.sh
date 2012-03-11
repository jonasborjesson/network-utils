#!/bin/sh

CLASSPATH="/home/jonas/.m2/repository/commons-cli/commons-cli/1.2/commons-cli-1.2.jar"
CLASSPATH="$CLASSPATH:/home/jonas/.m2/repository/org/jboss/netty/netty/3.2.7.Final/netty-3.2.7.Final.jar"
CLASSPATH="$CLASSPATH:/home/jonas/.m2/repository/log4j/log4j/1.2.14/log4j-1.2.14.jar"
CLASSPATH="$CLASSPATH:./target/classes"
echo $CLASSPATH
java -classpath $CLASSPATH com.jonasborjesson.network.udpsinkhole.UdpSinkHole $*
