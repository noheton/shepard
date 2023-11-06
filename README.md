# shepard releases

This project can be used to create automatic releases with release notes.
For more information about shepard, its usage and infrastructure, check out [the wiki](https://gitlab.com/dlr-shepard/documentation/-/wikis/home).

## Prerequisites

1. [Poetry](https://python-poetry.org/) is installed and configured on your system

## Preparation (only required once)

1. go to [gitlab.com](https://gitlab.com/-/profile/personal_access_tokens) and add a personal access token with `api` permissions
2. create a file `token.txt` and add your personal access token there
3. install dependencies: `poetry install --no-root`

## Usage

1. run the script by using `poetry run main.py`
2. fill in all the required information and create the release
