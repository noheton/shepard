# shepard scripts

This project provides useful scripts and tools to help maintaining shepard. For more information about shepard, its usage and infrastructure, check out [the wiki](https://gitlab.com/dlr-shepard/shepard/-/wikis/home).

## Prerequisites and preparation

1. [Poetry](https://python-poetry.org/) is installed and configured on your system
2. install dependencies: `poetry install`

## Preparation (only when you want to interact with the gitlab repository)

1. go to [gitlab.com](https://gitlab.com/-/profile/personal_access_tokens) and add a personal access token with `api` permissions
2. create a file `scripts/token.txt` and add your personal access token there

## Preparation (only required when you want to interact with a shepard instance)

1. go to your shepard instance and create an api key
2. create a file `scripts/api-key.txt` and add your api key there

## Usage

Run the script by using `poetry run cli [command] [args] [options]`

Possible commands are:

- `release`: Merge the develop branch into main and create a release
- `packages`: Delete outdated development packages from the gitlab registry
- `example-data`: Create example data on a given shepard instance

## Example

Create a release

```sh
poetry run cli release ./token.txt
```

Clean packages

```sh
poetry run cli packages ./token.txt
```

Create example data

```sh
poetry run cli example-data https://backend.example.com/shepard/api ./api_key.txt
```
