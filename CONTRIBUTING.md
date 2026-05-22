# Contributing to shepard

First off, thanks for taking time to contribute! The following is a set of guidelines for contributing to shepard. These are mostly guidelines, not rules. Use your best judgment, and feel free to propose changes to this document in a merge request.

When contributing to this repository, we differentiate between two contributions. Small changes and larger contributions. If you want to make a small change, you can create a merge request right away. If you want to add a new feature or make major changes to the codebase, we should discuss it [here](https://gitlab.com/dlr-shepard/shepard/-/issues).

Please note we have a code of conduct, please follow it in all your interactions with the project.

## Table of Contents

- [Code of Conduct](#code-of-conduct)
- [I don't want to read this whole thing I just have a question!](#i-dont-want-to-read-this-whole-thing-i-just-have-a-question)
- [Useful Links](#useful-links)
- [Branching Strategy](#branching-strategy)
- [Setting Up a Developing Environment](#setting-up-a-developing-environment)
- [How Can I Contribute?](#how-can-i-contribute)
- [Code Review Checklist](#code-review-checklist)

## Code of Conduct

Everyone participating in this project is asked to conduct themselves in a reasonable and proper manner. All interactions should be respectful and friendly, as is appropriate in a professional setting. By participating, you are accepting this CoC.

## I don't want to read this whole thing I just have a question!

Please don't file an issue to ask a question.
You'll get faster results by contacting us on [Mattermost at HZDR](https://mattermost.hzdr.de/signup_user_complete/?id=f5ycfi3nmigixxaerhpdg6q66y)

## Useful Links

- [Frontend Documentation](https://gitlab.com/dlr-shepard/shepard/-/wikis/Frontend)
- [Vue.js Documentation](https://v2.vuejs.org/v2/guide/)
- [BootstrapVue](https://bootstrap-vue.org/)
- [Backend Documentation](https://gitlab.com/dlr-shepard/shepard/-/wikis/Backend)
- [Guide: Writing Testable Code](http://misko.hevery.com/attachments/Guide-Writing%20Testable%20Code.pdf)
- [Jersey Documentation](https://eclipse-ee4j.github.io/jersey.github.io/documentation/latest3x/user-guide.html)
- [Tomcat Documentation](https://tomcat.apache.org/tomcat-10.0-doc/index.html)

## Branching Strategy

shepard uses three types of branches to organize development:

- **main:** The main branch is always stable. On this branch the releases are created.
- **develop:** This is the working branch for changes. The content of this branch will be regularly built and published as dev containers and packages. New changes are merged to develop first and will move to main as part of the release process.
- **feature:** Features and changes are developed on feature branches. Feature branches are only build on the server but not deployed to an environment. They are merged to develop using merge requests. Branches are deleted and the commits squashed to one merge commit when a feature branch is merged.

## Setting up a Developing Environment

### Fullstack Development Environment

- Follow the installation and configuration instructions for both front- and backend
- Make sure the `.env` files for front- and backend are available with correct values (see `.env.example` in the folders)
- install Docker and Docker Compose (alternatively Podman and Podman Compose)
- Run `docker compose --profile dev -f infrastructure-local/docker-compose.yml up -d` (alternatively `podman compose ...`)
- Set up your local keycloak instance:
  1. Navigate to http://localhost:8082
  2. Log in as admin with username `admin` and password `admin`
  3. Navigate to `Clients` > `Import client`
  4. Upload `infrastructure-local/keycloak_frontend-dev.json` as a resource file and hit `Save`
  5. Create a user via `Users` > `Add user`. Set credentials!
  6. Log out from keycloak
  7. Navigate to http://localhost:8082/realms/master/ and in `backend/.env` set the environment variable `OIDC_PUBLIC` to the public key obtained
- Run backend & frontend using `npm run start:dev`

### Frontend Development Environment

#### Downloads

- Node JS and NPM: <https://nodejs.org/en/>
- Vue CLI: <https://cli.vuejs.org/>
- Visual Studio Code: <https://code.visualstudio.com/>
- Volar: <https://marketplace.visualstudio.com/items?itemName=Vue.volar>
- Vue Devtools for Firefox: <https://addons.mozilla.org/en-US/firefox/addon/vue-js-devtools/>
- Vue Devtools for Chrome: <https://chrome.google.com/webstore/detail/vuejs-devtools/nhdogjmejiglipccpnnnanhbledajbpd>

#### Installation

- install Node JS and NPM
- install Vue CLI `npm install -g @vue/cli`
- install Visual Studio Code and the recommended plugins
- clone Git repository
- `cd` into the frontend folder
- open command line, type in `npm install .`

#### Local backend

If you don't have a working backend available, you can find a description of how to implement a local backend [here](#backend)

#### First run

- in the frontend folder, copy `.env.example` to `.env` and fill in the variables
- start the project: `npm run serve`

### Backend Development Environment

#### Downloads

- OpenJDK 17 (LTS): <https://adoptium.net/index.html?variant=openjdk17&jvmVariant=hotspot>
- Node.js v20.17.0 (LTS) and NPM: <https://nodejs.org/en/>
- One of the following IDEs:
  - VSCode: <https://code.visualstudio.com/download>
  - IntelliJ: <https://www.jetbrains.com/idea/download/>

> **WARNING** We mainly use the Ultimate Edition of IntelliJ.
> Although there is also a free Community Edition, there may be differences and limitations.

#### Installation

1. Install OpenJDK
2. Install Node.js
3. Install VSCode or IntelliJ
4. Clone Git repository
5. Run `npm install` in the top level
6. Add project to IDE:
   - IntelliJ:
     1. In the "Project Structure" settings go to "Modules" (or press F4).
     2. Click "+" and "Import Module".
     3. Select the "backend" folder.
     4. Select "Import Module from External Model".
     5. Select "Maven" and create.
     6. Click "ok" to save the new module.
     7. Wait for indexing of files.
     8. Afterwards you can use the existing run configuration on the top right to run quarkus.
   - VSCode:
     1. Install the recommended extensions in `.vscode/extensions.json` (VSCode should already suggest it)
     2. The "Debug Quarkus" configuration under "Run and Debug" in the left-side bar should start a working Quarkus instance

#### Configuration

The Backend configuration is environment dependant and specific properties need to be setup.
This setup is done using environment variables to override or append existing application properties in `application.properties` file according to [the Quarkus documentation](https://quarkus.io/guides/config-reference#env-file).
The variables preconfigured in `.env.example` also contain variables for local databases and frontend as described below.

1. Copy `.env.example` to `.env`
2. Enter valid OIDC parameters

#### Local databases

1. install Docker and Docker Compose (alternatively Podman and Podman Compose)
2. change to the `infrastructure-local` directory
3. run `docker-compose --profile dev up` (or `podman-compose --profile dev up`)
4. local instances of the databases will be launched and the storage will be persistent

| Service              | URL                      | Comment                           |
| -------------------- | ------------------------ | --------------------------------- |
| Keycloak frontend    | <http://localhost:8082/> | _user: admin, pw: admin_          |
| neo4j database       | <http://localhost:7687>  | _user: neo4j, pw: shepardshepard_ |
| neo4j frontend       | <http://localhost:7474>  |                                   |
| mongodb Database     | <http://localhost:27017> | _user: mongo, pw: shepard_        |
| mongodb Frontend     | <http://localhost:8084/> |                                   |
| timescaledb Database | <http://localhost:5432>  | _user: username, pw: password_    |
| postgis Database     | <http://localhost:5433>  | _user: username, pw: password_    |

The credentials can be overridden with environment variables.
Check the docker-compose file to find overridable variables.

#### First run

- start the project:
  - IntelliJ: use the existing run configuration on the top right to run quarkus.
  - VSCode: The "Debug Quarkus" configuration under "Run and Debug" in the left-side bar should start a working Quarkus instance
    > **Warning:** Using the VSCode run configuration "Debug Quarkus" above an opened file can edit the run configuration and cause issues.
    > Stick to the "Run and Debug" tab on the left side to avoid this.
  - Command line: Run `npm run start:dev` from the root directory
- visit your local frontend at <http://localhost:3000/user#api-keys> and generate an api key
- the Swagger UI for development and testing can then be found at <http://localhost:8080/shepard/doc/swagger-ui>

> **Hint:** If you don't have or want a local frontend and identity provider, you can easily generate an api key by running the integration tests
>
> 1. go to <http://localhost:7474/> and log in to your local neo4j database
> 2. obtain your api key with the following query:
>    `MATCH (a:ApiKey)-[:belongs_to]->(u:User {username: "test_it"}) RETURN a`
> 3. switch to the table view and copy the attribute `jws`, this is your api key

#### Running Tests

In order for all the tests to run we need empty databases prior to each run.
If you intend to run the tests we recommend using the compose file `infrastructure-local/docker-compose-backend-tests.yml` for the databases instead of the "standard" `docker-compose.yml`.
In this file there are no volumes defined so after shutting down the compose provider all data should be deleted.

Running Unit Tests:

Either start quarkus with `./mvnw quarkus:dev` and start the interactive test runner or run the following command:

```sh
./mvnw test
```

Running Integration Tests:

```sh
env SHEPARD_SPATIAL_DATA_ENABLED=true ./mvnw verify -P integration
```

Note that for the integration tests to work completely we need to set the environment variable `SHEPARD_SPATIAL_DATA_ENABLED` to `true`!

#### Known Issues

- VSCode
  - [Test runner for Java](https://marketplace.visualstudio.com/items?itemName=vscjava.vscode-java-test) has issues when the project is not built, build it first using `./mvnw package -DskipUTs`
  - Test runner added by the [Oracle Java extension](https://marketplace.visualstudio.com/items?itemName=Oracle.oracle-java) works without building first, but adds a duplicate extension since we prefer the Java-Package by Red Hat that is recommended by Microsoft.
    For that reason we don't use it.

## How Can I Contribute?

### Filing issues (this fork — `github.com/noheton/shepard`)

> The section below ("Issue Boards and Labels", "Architecture Proposals",
> "Bug Reports", "Feature Request") describes the **upstream** project's
> GitLab process. This subsection describes how this fork handles issues
> on GitHub. Both surfaces exist; pick the right one based on whether
> your issue is about upstream shepard or about a fork-specific feature.

This fork accepts issues on GitHub under five templates:

- **🐛 Bug report** — something is broken. Includes version + surface +
  reproduction. Auto-labels `type:bug` + `status:queued`.
- **✨ Feature request** — proposing a new capability. Includes a
  plugin-first heuristic dropdown that mirrors CLAUDE.md `§"Always:
  think plugin-first for new features"`. Auto-labels `type:feature` +
  `status:queued`.
- **📚 Docs improvement** — a doc is wrong / unclear / missing.
  Auto-labels `type:docs` + `status:queued`.
- **🔐 Security finding** — STUB ONLY. Real security findings go via
  private email to `fkrebs@nucli.de`, not via a public issue. The
  template just enforces this.
- **Blank issues are disabled.** The dropdown also surfaces links to
  `aidocs/16-dispatcher-backlog.md` (the backlog SSOT) and upstream
  Mattermost (for questions).

**The full backlog SSOT is [`aidocs/16-dispatcher-backlog.md`](aidocs/16-dispatcher-backlog.md),
not GitHub Issues.** Internal feature requests and backlog rows live
there. GitHub Issues are for things that need an external-visible
surface: bug reports, externally-driven feature requests, doc gaps,
and the currently-active backlog slices that an external contributor
could potentially claim. This avoids double-bookkeeping a 100-row
ledger across two stores.

See [`aidocs/strategy/83-github-features-leverage.md`](aidocs/strategy/83-github-features-leverage.md)
for the full GitHub-features decision matrix.

### Issue Boards and Labels

We use issue [labels](https://docs.gitlab.com/ee/user/project/labels.html) and [boards](https://docs.gitlab.com/ee/user/project/issue_board.html) to manage ongoing issues. The templates for issues and merge requests usually take care of setting the necessary labels, so you don't have to do that. Everything else is managed by our maintainers.

### Architecture Proposals

If you have a proposal to change things in Shepard that affect the entire ecosystem, you should submit an issue [here](https://gitlab.com/dlr-shepard/shepard/-/issues) and label it with the label "architecture". Please provide as much detail as possible by filling in this [template](.gitlab/issue_templates/default.md). This will help the upcoming discussion.

Once the discussion has reached a conclusion, you can create a merge request submitting your proposed changes to the concepts.

### Bug Reports

This section guides you through submitting a bug report for the shepard frontend. Following these guidelines helps maintainers and the community understand your report, reproduce the behavior, and find related reports.

Bugs are tracked as GitLab issues. When you are creating a bug report, please include as many details as possible. Create an issue on that repository and provide the following information by filling in this [template](.gitlab/issue_templates/Bug.md). Label your issue with the appropriate label, e.g. "frontend", "backend" or "deployment". This will help us to resolve issues faster. The template can be selected when creating a new issue.

> **Note:** If you find a **Closed** issue that seems like it is the same thing that you're experiencing, open a new issue and include a link to the original issue in the body of your new one.

### Feature Request

This section guides you through submitting a feature request for shepard, including completely new features and minor improvements to existing functionality. Following these guidelines helps maintainers and the community understand your suggestion.

Feature requests are tracked as GitLab issues. When you are creating a feature request, please include as many details as possible. Create an issue in this repository and provide the following information by filling in this [template](.gitlab/issue_templates/Feature.md). Label your issue with the appropriate label, e.g. "frontend", "backend" or "deployment". Explain the requested feature and include additional details to help maintainers understand your request. This will help us to resolve issues faster. As before, the template can be selected when creating a new issue.

### Your First Code Contribution

Unsure where to begin contributing to shepard? You can start by looking through these [beginner issues](https://gitlab.com/dlr-shepard/shepard/-/issues?scope=all&state=opened&label_name[]=beginner)

### Contributing to the Architectural Documentation

The architectural documentation under `architecture/` follows the [arc42 template](https://arc42.org/overview). It is written using [AsciiDoc](https://docs.asciidoctor.org/asciidoc/latest/syntax-quick-reference/) with diagrams written in [PlantUML](https://plantuml.com/en/). Details and reasoning of the architecture of shepard can be found there. Make sure to update the documentation e.g. if your contribution changes the architecture or introduces new concepts to shepard.

### Merge Request Process

This section guides you through submitting a merge request for shepard. Following these guidelines helps maintainers to review your merge request faster.

First, create a new branch based on `develop`. You can create a branch within the repository if you have the necessary permissions. If not, you can always fork the repository and create a new branch there. Now you are ready to begin with your contribution.

Once you have created an initial prototype of your contribution, commit your changes to GitLab and open a merge request with a meaningful name. Open up your merge request to `develop` as soon as possible and mark it as [draft](https://docs.gitlab.com/ee/user/project/merge_requests/drafts.html) so everyone knows you are working on an issue. This prevents duplicate work and unhappy contributors. Please fill in the required [template](.gitlab/merge_request_templates/default.md) and follow the [code review checklist](#code-review-checklist) below. As before with issues, the template can be selected when creating a new merge request.

After your first commit, you can add as many additional commits as you like. After your contribution is accepted, your changes will be squashed into one commit anyway.

While you are working on your contribution, others may merge their merge request. To keep up with current developments, it is a good idea to occasionally [rebase](https://docs.gitlab.com/ee/topics/git/git_rebase.html#git-rebase) your branch to the current `develop` branch.

## PR discipline (this fork)

When opening a PR against `github.com/noheton/shepard`, the
[`.github/pull_request_template.md`](.github/pull_request_template.md)
checklist mirrors the "Always:" rules from
[`CLAUDE.md`](CLAUDE.md). The short version:

1. **Title follows Conventional Commits** with the `aidocs/16` row ID
   as the scope: `feat(VIS-T1): ...`, `fix(IMPORT-W2): ...`. The
   auto-categorisation in [`.github/release.yml`](.github/release.yml)
   relies on this.
2. **Upgrade-path ledger** (`aidocs/34-upstream-upgrade-path.md`) gets
   a row if anything an upstream admin would notice has changed
   (config keys, endpoints, schemas, defaults, dependencies, breaking
   behaviour).
3. **Vision** (`aidocs/42-vision.md`) gets updated if this is
   user-visible (new payload kind, top-level concept, etc.).
4. **Feature matrix** (`aidocs/44-fork-vs-upstream-feature-matrix.md`)
   gets the relevant status-symbol flip.
5. **Tests in the same PR.** Backend coverage floor: ≥ 60% line + 60%
   branch; new code targets ≥ 70% line. Frontend: Vitest test per
   feature.
6. **Security gates green** — SpotBugs + findsecbugs, CodeQL, OWASP
   Dependency-Check, Trivy, gitleaks, dependency-review. Any new
   finding either gets fixed or gets a suppression with justification
   in the same PR.
7. **Plugin-first heuristic considered** for new features. New payload
   kinds + external integrations default to `plugins/<id>/`. If it
   lands in-tree, the linked design doc says why.
8. **Operator runtime knob** as `:*Config` Neo4j entity + admin REST +
   CLI parity if introducing a feature flag / retention window / cap
   (per the A3b / N1c2 / UH1a pattern).
9. **Plugin docs trio** (`plugins/<id>/docs/{reference,quickstart,install}.md`)
   if this is plugin code.
10. **User-facing docs** in `docs/reference/<feature>.md` if
    user-visible.

The full rules live in [`CLAUDE.md`](CLAUDE.md). The PR template is
your forcing function.

## Code Review Checklist

### General

- The code on the develop branch is always functional.
- All files are formatted according to our prettier configuration
- Changes in merge requests are complete, self-contained, and implement only a single functionality.
- Changes that do not belong to an existing merge request are implemented in a separate merge request.
- The existing package structure is not violated by the new code.
- There is no commented out code for the develop branch.
- No console output (`System.out.println()` or `console.log()`) is used on the develop branch.
- No warnings occur during compilation.
- Do not include unnecessary packages.
- Included packages should be backed by a company or a large community.
- Don't add comments to obvious things.
- Write meaningful log messages.

### Frontend Code

- Use the latest JavaScript standard.
- Define components using Vue [Single File Components](https://vuejs.org/api/sfc-spec.html) and [script setup](https://vuejs.org/api/sfc-script-setup.html).
- Do not change the `package-lock.json` unless you have included or updated a package.

### Backend Code

- Added functionality is tested by unit tests.
- Regular use cases are tested with integration tests.
- Do not introduce dependency cycles.
- The OpenAPI documentation is consistent with the actual behavior of the software.
- Classes and public methods are uniformly documented using javadoc comments.
- If you need any of the features of objects, use them otherwise use primitives.
- Try to offload as much work as possible to the databases.
- Parallelize things only when needed (e.g. if more than 1000 objects will occur in a stream).
- Use Jakarta Bean Validation to validate user input.
