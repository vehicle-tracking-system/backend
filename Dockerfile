FROM arm64v8/openjdk:11

WORKDIR /opt/app
COPY server.jar server.jar
COPY create.sql create.sql

ENTRYPOINT ["java", "-jar", "/opt/app/server.jar"]
