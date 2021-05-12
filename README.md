# Backend for vehicle tracking system

The project can be deployed as Docker image. For this purposes bash script `deploy.sh` is prepared.
The script provides some flags (list of flags with short description is in table bellow) to modify how the server will be deployed.

| Flag | Description |
| --- | ----------- |
| -m | Deploy MQTT broker and server. |
| -f | Absolute path to fat JAR file (MUST be provided if JAR file is in another directory then script) |
| -j | Build only fat JAR. Server is not deployed as Docker image. |
| -s | Pack server's project with `sbt-native-packager` and deployed them as Docker container on local machine.|
 
You can run the server with:
```bash
sbt run
```

or use build script:
```bash
./deploy.sh -s
```

## Configuration

The configuration is done by environment variables set on a machine where server will be run.

| Variable | Type | Description |
| --- | ----------- | ----------- |
| JWT_SECRET | string |This secret is used to sign issued JWT tokens.|
| JWT_EXPIRATION | int | Lifetime of issued JWT tokens in seconds. |
| MQTT_HOST | string | URL of MQTT broker to connect.|
| MQTT_PORT | int | PORT of MQTT broker. |
| MQTT_SSL | boolean | If true, secure connection to broker is used. Otherwise the connection between broker and server is unsecured.  |
| MQTT_TOPIC | string | From topic with this name, messages from trackers will be read. |
| MQTT_USER | string | Username for connection to MQTT broker. |
| MQTT_PASSWORD | string | Password for connection to MQTT broker. |
| MQTT_SUBSCRIBER_NAME | string | Name of server that used as identifier on broker. |
| FRONTEND_DIR | string | Path to directory, where are frontend files. |
| TRACKS_DIR | string | Path to directory, where exported tracks will be save. |
| DB_JDBC_URL | string | JDBC connection string to database (engine can be changed in `reference.conf`, default is H2) |
| DB_PASSWORD | string | Password for access the database. |
| DB_USERNAME | string | Username for access the database. |

If you change any other settings in `reference.conf` it can be dangerous for system stability.
