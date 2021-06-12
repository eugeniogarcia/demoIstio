FROM pruebacontenedor.azurecr.io/openjdk:8u212-jre-alpine
VOLUME /tmp
COPY target/app.war app.war
ENTRYPOINT ["java","-Djava.security.egd=file:/dev/./urandom","-jar","/app.war"]
