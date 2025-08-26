import json

from ruamel.yaml import YAML

TS_CLIENT = "../clients/tests/typescript/package.json"
DOCKER = "../infrastructure/docker-compose.yml"


"""
Python script that changes the version number of shepard in the typescript tests and
docker compose file.

The version number is the only argument to this script.
"""


def change_version(version: str):
    _update_ts_client(version)
    _update_docker_compose(version)


def _update_ts_client(new_version: str):
    with open(TS_CLIENT) as f:
        j = json.loads(f.read())
    j["dependencies"]["@dlr-shepard/shepard-client"] = new_version
    out = json.dumps(j, indent=2)
    with open(TS_CLIENT, "w") as f:
        f.write(out)


def _update_docker_compose(new_version: str):
    yaml = YAML()
    yaml.default_flow_style = False
    yaml.sort_keys = False
    yaml.preserve_quotes = True
    yaml.indent(mapping=2, sequence=4, offset=2)
    yaml.width = 120

    with open(DOCKER) as f:
        y = yaml.load(f)

    # update backend image
    registry_key, old_version = y["services"]["backend"]["image"].split(":")
    y["services"]["backend"]["image"] = f"{registry_key}:{new_version}"

    # update frontend image
    registry_key, old_version = y["services"]["frontend"]["image"].split(":")
    y["services"]["frontend"]["image"] = f"{registry_key}:{new_version}"

    with open(DOCKER, "w") as f:
        yaml.dump(y, f)
