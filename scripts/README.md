# shepard scripts

This directory provides useful scripts and tools to help maintaining shepard.

## Prerequisites and preparation

1. [Poetry](https://python-poetry.org/) is installed and configured on your system
2. install dependencies: `poetry install`

### Preparation (only when you want to interact with the gitlab repository, i.e., create a new release)

1. go to [Personal Access Tokens (GitLab)](https://gitlab.com/-/user_settings/personal_access_tokens) and add a personal access token with `api` permissions
2. create a file `scripts/token.txt` and add your personal access token there

### Preparation (only required when you want to interact with a shepard instance)

1. go to your shepard instance and create an api key
2. create a file `scripts/api-key.txt` and add your api key there

## Usage

Run the script by using `poetry run cli [command] [args] [options]`

Possible commands are:

- `release`: Collects all changes on the main branch since the last release and creates a release page with an automatically generated changelog page. Furthermore, it also creates a new release tag on the main branch, that triggers the release pipeline. If you want to create a new release, please carefully read the documentation before: [Release Process Documentation](https://shepard-dlr-shepard-e573f5a4116ef73f64fe76039b5c0aad01da3a88afa.gitlab.io/architecture-docs/#_release_process).
- `packages`: Delete outdated development packages from the gitlab registry
- `example-data`: Create example data on a given shepard instance

## Example

Create a regular release

```sh
poetry run cli release ./token.txt
```

Create a hotfix release

```sh
poetry run cli release --hotfix-release ./token.txt
```

Clean packages

```sh
poetry run cli packages ./token.txt
```

Create example data

```sh
poetry run cli example-data https://backend.example.com/shepard/api ./api_key.txt
```
