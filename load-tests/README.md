# Shepard load tests

Load tests are executed with [grafana/k6](https://k6.io).
They use the backend on the dev instance to have a single reference system for measurements.

We use a docker image for executing tests so that no local installation of k6 is necessary.
If you run load tests for the first time, the docker image will be downloaded.

K6 is collecting some metrics during test execution.
Those metrics are reported directly to prometheus.
We can use grafana to visualize those metrics.
In order to make that working, you have to use the docker compose file in the backend folder.
That will start prometheus and grafana with the correct settings and the correct network.

## How to run load tests?

- Run `docker compose up` from the backend folder.
- Create a copy of file `./mount/settings.example.json` and name it `settings.json` in the same folder.
  - Provide an api key that can be used to access the backend.
    You can create an api key with help of the frontend easily.
- Use the script `run-load-test.sh` and provide the file name of the load test that should be executed as parameter.
