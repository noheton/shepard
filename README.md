# shepard

This is the source code repository for the shepard core system.
It is designed as a monorepo containing frontend, backend and documentation.

To find more information about shepard, its usage and infrastructure, check out [the documentation concept](https://gitlab.com/dlr-shepard/shepard/-/blob/main/architecture/shepard-architectural-documentation.adoc?ref_type=heads#user-content-documentation-artifacts).

## Documentation site

A GitHub Pages site is published from `docs/` on this mirror:

**<https://noheton.github.io/shepard/>**

Tour: overview & use cases (`/`) · architecture (`/architecture`) · Python quickstart (`/getting-started`) · user guide (`/user-guide`) · admin guide (`/admin`) · system requirements (`/system-requirements`) · [deploy options](https://noheton.github.io/shepard/deploy) (Oracle Free, self-hosted behind Zoraxy, paid VPS, managed-services split).

Source under `docs/`; built and deployed by `.github/workflows/pages.yml` on push to `main`. The canonical authoritative documentation is the GitLab wiki linked further down in this README; the GitHub Pages site is a structured overview for the GitHub mirror.

## Quick test / evaluation setup

There is a Docker Compose configuration in `infrastructure-local` which you can use to quickly try out shepard.
It contains the databases, some monitoring tools, an oidc provider as well as the shepard frontend and backend.

## Getting started as a user

- The respective OpenAPI definition can be found at `<your backend url>/shepard/doc/openapi.json`.
- This specification can be used to create arbitrary clients with the [OpenAPI Generator](https://openapi-generator.tech/).
- Clients for Python, Java and Typescript are created and [deployed automatically via Gitlab CI/CD](https://gitlab.com/groups/dlr-shepard/-/packages).

Further information for users of shepard can be found [here](https://gitlab.com/dlr-shepard/shepard/-/wikis/rest-api).

## Getting started as an administrator

Information about the deployment can be found [here](https://gitlab.com/dlr-shepard/shepard/-/tree/main/infrastructure).

## Getting started as a contributor

Shepard is open for contributions.
Information on how to contribute can be found [here](CONTRIBUTING.md).
