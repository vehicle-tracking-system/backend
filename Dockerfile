FROM arm64v8/openjdk:11
WORKDIR /
COPY qemu-aarch64-static /usr/bin

WORKDIR /opt/app
COPY target/scala-2.13/tracker-server-assembly-1.0.jar server.jar

RUN rm -rf /usr/bin/qemu-aarch64-static

ENTRYPOINT ["java", "-jar", "/opt/app/server.jar"]
