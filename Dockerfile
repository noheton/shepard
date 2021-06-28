FROM tomcat:9.0.46-jdk11-openjdk-slim

COPY server.xml /usr/local/tomcat/conf/
COPY target/backend.war /usr/local/tomcat/webapps/shepard.war

CMD ["catalina.sh", "run"]
