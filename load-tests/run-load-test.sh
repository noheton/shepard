#!/bin/bash
set -e # abort script if any of the commands returns non-zero

# Print out help text if no filename was provided to this script
if [ "$#" -eq 0 ]; then
    echo "Missing script parameter"
    echo "The load test script expects a filename or test name as parameter."
    echo "For example you can call this script either with: './run-load-test.sh src/collections/example-test.ts' or './run-load-test.sh collections/example-test'."
    exit 1
fi

DOCKER_NETWORK=backend_shepard
K6_PROMETHEUS_RW_SERVER_URL=http://prometheus:9090/api/v1/write
LOAD_TEST_NAME=$1                               # first parameter is file name of load test -> load test name is expected to be either: collections/smoke-test or src/collections/smoke-test.ts
DIST_FILE_PATH=${LOAD_TEST_NAME##src/}          # Remove 'src/' in front of test file name
DIST_FILE_PATH=${DIST_FILE_PATH%%.ts}.bundle.js # Build file path for bundled test
DIST_FILE_PATH=//var/dist/${DIST_FILE_PATH}     # Map filepath to docker paths

echo Transpiling and bundling typescript files
npm run bundle

echo Running load test from \"$DIST_FILE_PATH\" now

docker run \
    --network $DOCKER_NETWORK \
    --name shepard-k6 \
    --rm -t \
    -v //$(pwd)/mount://var/k6:ro \
    -v //$(pwd)/dist://var/dist:ro \
    -e K6_PROMETHEUS_RW_SERVER_URL=$K6_PROMETHEUS_RW_SERVER_URL \
    -e K6_WEB_DASHBOARD=true \
    -p 5665:5665 \
    grafana/k6 run --out experimental-prometheus-rw ${DIST_FILE_PATH}
