# shepard scripts

This project provides useful scripts and tools to help maintain the shepard repository.
For more information about shepard, its usage and infrastructure, check out [the wiki](https://gitlab.com/dlr-shepard/documentation/-/wikis/home).

## Prerequisites

1. [Poetry](https://python-poetry.org/) is installed and configured on your system

## Preparation (only required once)

1. go to [gitlab.com](https://gitlab.com/-/profile/personal_access_tokens) and add a personal access token with `api` permissions
2. create a file `token.txt` and add your personal access token there
3. install dependencies: `poetry install`

## Usage

Run the script by using `poetry run cli [command] [args] [options]`

Possible commands are:

- `release`: Create a Gitlab release for the given project
- `packages`: Delete old development packages for the given project

## Example

Clean packages

```sh
poetry run cli packages --project=backend ./token.txt
```

Create a frontend release

```sh
poetry run cli release ./token.txt
```
