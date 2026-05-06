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
2. go to `shepard/scripts` and create a file `.env`. Therefore see `.env.example`.
3. Fill in the missing variables: BACKEND_URL and API_KEY.

## Usage

Run the script by using `poetry run cli [command] [args] [options]`

Possible commands are:

- `release`: Collects all changes on the main branch since the last release and creates a release page with an automatically generated changelog page. Furthermore, it also creates a new release tag on the main branch, that triggers the release pipeline. If you want to create a new release, please carefully read the documentation before: [Release Process Documentation](https://shepard-dlr-shepard-e573f5a4116ef73f64fe76039b5c0aad01da3a88afa.gitlab.io/architecture-docs/#_release_process).
- `packages`: Delete outdated development packages from the gitlab registry
- `sample`: Create example data on a given shepard instance

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

Create the inner solar system sample data for a given shepard instance.
A valid api key and the backend url need to be provided as part of the environment.
You may use the .env.example, copy it to .env and fill in both values for it to take effect.

```sh
poetry run cli sample
```

## Standalone shell scripts

### `check-schema-name.sh`

Lints that every backend IO/DTO class has a class-level
`@Schema(name = "...")` so the OpenAPI generator emits a stable schema
name (a class rename then no longer breaks every downstream client).
Existing offenders are tracked in
`backend/src/main/resources/schema-name-baseline.txt`; the lint passes
only if the missing set is a subset of the baseline.

```sh
bash scripts/check-schema-name.sh
```
