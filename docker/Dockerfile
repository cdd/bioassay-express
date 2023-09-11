# BAE docker setup for internally hosted database

FROM ubuntu:20.04

#WORKDIR /run

EXPOSE 8080

RUN apt-get update
RUN DEBIAN_FRONTEND="noninteractive" apt-get install -y nodejs npm

RUN apt-get update
RUN apt-get install -y apt-transport-https ca-certificates wget dirmngr gnupg software-properties-common
RUN wget -qO - https://adoptopenjdk.jfrog.io/adoptopenjdk/api/gpg/key/public | apt-key add -
RUN add-apt-repository --yes https://adoptopenjdk.jfrog.io/adoptopenjdk/deb/
RUN apt-get update
RUN apt-get install -y adoptopenjdk-16-hotspot
RUN apt-get install -y telnet less curl zip unzip
RUN apt -y install mongodb

WORKDIR /opt
RUN pwd
RUN ls -la .
COPY tomcat.tar.gz .
RUN tar xvzf tomcat.tar.gz
RUN mv apache-tomcat-9* tomcat
WORKDIR /opt/tomcat
COPY BioAssayExpressFullDB.war webapps/BioAssayExpress.war

COPY assays_block*.zip ./
COPY provisional_terms.ttl .
COPY baeconsole .
RUN chmod +x baeconsole
RUN mkdir tmp
RUN unzip webapps/BioAssayExpress.war -d tmp
COPY execute.sh .

CMD sh execute.sh

