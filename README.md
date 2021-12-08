# shepard backend

This is the source code repository for the shepard backend written in Java.
For more information about shepard, its usage and infrastructure, check out [the wiki](https://gitlab.com/dlr-shepard/documentation/-/wikis/home).

## Important links

- [Our Developer Documentation](https://dlr-shepard.gitlab.io/backend/)
- [The latest OpenAPI documentation](https://gitlab.com/dlr-shepard/backend/-/jobs/artifacts/master/file/target/swagger/swagger.yaml?job=build)
- [The latest XRef documentation](https://gitlab.com/dlr-shepard/backend/-/jobs/artifacts/master/file/target/site/xref/index.html?job=site)

## Getting started as an administrator

Information about the deployment can be found [here](https://gitlab.com/dlr-shepard/deployment).

## Getting started as a contributor

shepard is open for contributions.
Information on how to contribute can be found [here](CONTRIBUTING.md).

## Getting started as a user

- The respective OpenAPI definition can be found at `/shepard/doc/openapi.json`.
- This specification can be used to create arbitrary clients with the [OpenAPI Generator](https://openapi-generator.tech/).
- Clients for Python, Java and Typescript are created and deployed automatically via Gitlab CI/CD.
- Further clients can be created manually with the [openapi generator](https://openapi-generator.tech/).
