#!/bin/bash

#########################################
# Program Your Home - Boot Alexa proxy  #
#########################################

cd /home/pyh/alexa-proxy
# Start the server
mvn exec:java -Dexec.mainClass="com.programyourhome.alexa.proxy.AlexaProxySpringBootApplication" -Dalexa.proxy.properties.location=src/test/resources/alexa.proxy.prod.properties
