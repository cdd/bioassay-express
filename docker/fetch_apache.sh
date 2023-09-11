#!/bin/sh
# note: apache version changes frequently, so adjust as necessary
# if this doesn't work, go here:
#   https://tomcat.apache.org/download-90.cgi
# grab the latest tarball and rename it to tomcat.tar.gz
VERSION=9.0.80
curl -O "https://dlcdn.apache.org/tomcat/tomcat-9/v${VERSION}/bin/apache-tomcat-${VERSION}.tar.gz"
mv apache-tomcat-${VERSION}.tar.gz tomcat.tar.gz

