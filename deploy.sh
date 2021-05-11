#!/bin/bash

CLEAN=false
WITH_MQTT=false
SBT_BUILD=false
BUILD_JAR=false
NEW_JAR_PATH="./server.jar"

usage() {
  echo "Usage: $0 [-m] [-c] [-f <path>] [-p <path>]" 1>&2
  exit 1
}

while getopts "cmsjf:p:" o; do
  case "${o}" in
  c)
    CLEAN=true
    ;;
  m)
    WITH_MQTT=true
    ;;
  s)
    SBT_BUILD=true
    ;;
  j)
    BUILD_JAR=true
    ;;
  f)
    JAR_PATH=${OPTARG}
    cp "$JAR_PATH" ./server.jar || echo "FAILED"
    exit 1
    ;;
  p)
    NEW_JAR_PATH=${OPTARG}
    echo "Path to new generated JAR: $NEW_JAR_PATH"
    ;;
  '?')
    echo "INVALID OPTION -- ${OPTARG}" >&2
    usage
    exit 1
    ;;
  *)
    usage
    exit 1
    ;;
  esac
done

if [ "$SBT_BUILD" = true ] && [ "$BUILD_JAR" = true ]; then
  echo 'Unavailable combination of flags'
  exit 1
fi

if [ "$CLEAN" = true ]; then
  echo 'Cleaning...' &&
  sbt clean &&
  echo 'Cleaned' ||
  echo 'Cleaning failed'; exit 1
fi

if [ "$BUILD_JAR" = true ]; then
  echo 'Building fat JAR' &&
  sbt ";set assemblyOutputPath in assembly := file(\"$NEW_JAR_PATH\"); assembly" &&
  echo 'Fat JAR successfully build' ||
  echo 'Building fat JAR failed'; exit 1
fi

if [ "$SBT_BUILD" = true ]; then
  echo 'Building Docker image using SBT' &&
    sbt docker:publishLocal
else
  echo 'Building Docker image from fat JAR' &&
  docker build . -t cz.cvut.fit/tracker-server:1.0 &&
  echo 'SBT build done' ||
  echo 'SBT build failed'; exit 1
fi

if [ "$WITH_MQTT" = true ]; then
  echo 'Deploying tracker server with MQTT broker' &&
  docker-compose -f docker-compose-with-mqtt.yml up -d --force-recreate &&
  echo 'Server and MQTT broker successfully deployed' ||
  echo 'Deploying FAILED'; exit 1
else
  echo 'Deploying tracker server' &&
  docker-compose -f docker-compose.yml up -d --force-recreate &&
  echo 'Server successfully deployed' ||
  echo 'Deploying FAILED'; exit 1
fi
