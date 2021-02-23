# Scala Server Toolkit Seed

This is an example that shows how Scala Server Toolkit can be used to properly
initialize a server application.

Some of the "business logic" parts are simplified so please do not take them as
the best example of how server application should be structured.

You need to run Docker Compose to start up the environment for the application:

```bash
docker-compose -f docker-compose.yml up
```

Then you can just run the [Main](src/main/scala/com/avast/sst/example/Main.scala) class:

```bash
sbt run
```

Try the following requests to test the example application endpoints:

```bash
curl -v http://localhost:8080/hello
```

```bash
curl -v http://localhost:8080/random
```

```bash
curl -v http://localhost:8080/circuit-breaker
```
