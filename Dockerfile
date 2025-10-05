FROM tomcat:9.0-jdk17
COPY myapp.war /usr/local/tomcat/webapps/
EXPOSE 8080
CMD ["catalina.sh", "run"]
