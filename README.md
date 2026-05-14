# shepard

> ## ⚠️ EXPERIMENTAL FORK — NOT THE PRODUCTION VERSION
>
> **This repository is an experimental fork of [shepard](https://gitlab.com/dlr-shepard/shepard).**
> It is maintained independently of the upstream project and contains
> **unreleased, unvalidated changes** that are not part of the production
> shepard release line.
>
> - **Production users:** use upstream
>   [`gitlab.com/dlr-shepard/shepard`](https://gitlab.com/dlr-shepard/shepard) —
>   that is the canonical, supported version.
> - **This fork:** a development / research workspace. Features land here
>   first and may change shape, get reverted, or never reach upstream.
>   APIs under `/v2/...` are explicitly the fork's development surface
>   and are not stable. Compose / config / schema details may shift
>   between commits.
> - **Upstream-API compatibility:** the `/shepard/api/...` surface stays
>   byte-frozen against upstream 5.2.0, so a client built against
>   upstream keeps working. New endpoints land under `/v2/...` only.
>
> Do not run this fork in production unless you understand the above
> trade-offs. See [`aidocs/34-upstream-upgrade-path.md`](aidocs/34-upstream-upgrade-path.md)
> for the change ledger; [`aidocs/44-fork-vs-upstream-feature-matrix.md`](aidocs/44-fork-vs-upstream-feature-matrix.md)
> for the per-feature status; [`aidocs/63-architecture-decision-log.md`](aidocs/63-architecture-decision-log.md)
> for the architectural decisions.

This is the source code repository for the shepard core system.
It is designed as a monorepo containing frontend, backend and documentation.

To find more information about shepard, its usage and infrastructure, check out [the documentation concept](https://gitlab.com/dlr-shepard/shepard/-/blob/main/architecture/shepard-architectural-documentation.adoc?ref_type=heads#user-content-documentation-artifacts).

## Documentation site

A GitHub Pages site is published from `docs/` on this mirror:

**<https://noheton.github.io/shepard/>**

Tour: overview & use cases (`/`) · architecture (`/architecture`) · Python quickstart (`/getting-started`) · user guide (`/user-guide`) · admin guide (`/admin`) · system requirements (`/system-requirements`) · [deploy options](https://noheton.github.io/shepard/deploy) (Oracle Free, self-hosted behind Zoraxy, paid VPS, managed-services split).

Source under `docs/`; built and deployed by `.github/workflows/pages.yml` on push to `main`. The canonical authoritative documentation is the GitLab wiki linked further down in this README; the GitHub Pages site is a structured overview for the GitHub mirror.

## Try it locally — `make demo-up`

> ```bash
> git clone https://github.com/noheton/shepard.git
> cd shepard
> make demo-up
> ```

One command brings up a complete, seeded shepard instance at **<http://localhost:3000/>** — backend, frontend, Neo4j, MongoDB, TimescaleDB, Keycloak, four shipped plugins (`unhide`, `kip`, `minter-local`, `reference-dbpedia-databus`), and example data already loaded (five Collections covering engineering / climate / lab-journal / private / public-showcase use cases). Log in as `alice / alice-demo` (Manager), `bob / bob-demo` (Reader), or `admin / admin-demo` (instance-admin).

Runs in well under 90 seconds (after the first image build). Full operator runbook: [`infrastructure-local/README-demo.md`](infrastructure-local/README-demo.md). Public-docs entry point: [`docs/reference/deployment-quickstart.md`](docs/reference/deployment-quickstart.md).

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
