FROM tomcat:10.1.12-jdk17-temurin

COPY server.xml /usr/local/tomcat/conf/
COPY target/shepard.war /usr/local/tomcat/webapps/shepard.war

CMD ["catalina.sh", "run"]
