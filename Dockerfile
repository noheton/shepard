FROM tomcat:10.0.20-jdk17-temurin-focal

COPY server.xml /usr/local/tomcat/conf/
COPY target/shepard.war /usr/local/tomcat/webapps/shepard.war

CMD ["catalina.sh", "run"]
