FROM tomcat:9.0.48-jdk11-openjdk-slim

COPY server.xml /usr/local/tomcat/conf/
COPY target/shepard.war /usr/local/tomcat/webapps/shepard.war

CMD ["catalina.sh", "run"]
