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

Please don't file an issue to ask a question. You'll get faster results by contacting us on [Mattermost at HZDR](https://mattermost.hzdr.de/signup_user_complete/?id=f5ycfi3nmigixxaerhpdg6q66y)

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

### Frontend

#### Downloads

- Node JS and NPM: <https://nodejs.org/en/>
- Vue CLI: <https://cli.vuejs.org/>
- Visual Studio Code: <https://code.visualstudio.com/>
- Volar: <https://marketplace.visualstudio.com/items?itemName=Vue.volar>
- TypeScript Vue Plugin (Volar) <https://marketplace.visualstudio.com/items?itemName=Vue.vscode-typescript-vue-plugin>
- Vue Devtools for Firefox: <https://addons.mozilla.org/en-US/firefox/addon/vue-js-devtools/>
- Vue Devtools for Chrome: <https://chrome.google.com/webstore/detail/vuejs-devtools/nhdogjmejiglipccpnnnanhbledajbpd>

#### Installation

- install Node JS and NPM
- install Vue CLI `npm install -g @vue/cli`
- install Visual Studio Code and the recommended plugins
- clone Git repository
- `cd` into the frontend folder
- open command line, type in `npm install .`

#### Type Support for `.vue` Imports in TS (optional)

1. Disable the built-in TypeScript Extension
   1. Run `Extensions: Show Built-in Extensions` from VSCode's command palette
   2. Find `TypeScript and JavaScript Language Features`, right click and select `Disable (Workspace)`
2. Reload the VSCode window by running `Developer: Reload Window` from the command palette.

#### Local backend

If you don't have a working backend available, you can find a description of how to implement a local backend [here](#backend)

#### First run

- fill in the variables in your environment file [/frontend/.env.development](/frontend/.env.development)
- start the project: `npm run serve`

### Backend

#### Downloads

- OpenJDK 17 (LTS): <https://adoptium.net/index.html?variant=openjdk17&jvmVariant=hotspot>
- Eclipse IDE for Enterprise Java and Web Developers: <https://www.eclipse.org/downloads/packages/>
- Apache Tomcat 10: <https://tomcat.apache.org/download-10.cgi>
- Project Lombok: <https://projectlombok.org/download>

#### Installation

1. install OpenJDK
2. install Eclipse IDE for Enterprise Java and Web Developers
3. unpack Apache Tomcat
4. install Project Lombok (run JAR file and follow the installer)
5. clone Git repository
6. import project into Eclipse (File -> Open Projects from File System...)
7. link Tomcat with Eclipse (create a new server in tab `Servers` and select Tomcat)

#### Local databases

1. install Docker and Docker Compose (alternatively Podman and Podman Compose)
2. change to the project root directory (backend folder)
3. run `docker-compose up` (or `podman-compose up`)
4. local instances of the databases will be launched without persistent storage
5. quick tip: run the integration tests to fill your databases with some content

| Service           | URL                      | Comment                     |
| ----------------- | ------------------------ | --------------------------- |
| shepard Frontend  | <http://localhost:8081/> | _Requires Keycloak_         |
| neo4j Database    | <http://localhost:7687>  | _user: neo4j, pw: shepard_  |
| neo4j Frontend    | <http://localhost:7474>  |                             |
| mongodb Database  | <http://localhost:27017> | _user: mongo, pw: shepard_  |
| mongodb Frontend  | <http://localhost:8084/> |                             |
| influxdb Database | <http://localhost:8086>  | _user: influx, pw: shepard_ |
| influxdb Frontend | <http://localhost:8888>  |                             |
| Swagger UI        | <http://localhost:8082>  |                             |

#### First run

> If you don't have a local frontend and identity provider, you can easily generate an api key by running the integration tests

1. start the project: right click on the project -> Run on Server
2. run the integration tests: right click on `src/test/java/en/dlr/shepard/integrationtests` -> Run as JUnit Test
3. go to <http://localhost:7474/> and log in to your local neo4j database
4. obtain your api key with the following query: `MATCH (a:ApiKey)-[:belongs_to]->(u:User {username: "test_it"}) RETURN a`
5. switch to the table view and copy the attribute `jws`, this is your api key

## How Can I Contribute?

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

## Code Review Checklist

### General

- The code on the main branch is always functional.
- There is no commented out code for the main branch.
- No console output (`System.out.println()` or `console.log()`) is used on the main branch.
- All files except Java files are formatted according to our prettier configuration
- Changes in merge requests are complete, self-contained, and implement only a single functionality.
- Changes that do not belong to an existing merge request are implemented in a separate merge request.

### Frontend Code

- The existing package structure is not violated by the new code.
- Use the latest JavaScript standard.
- Define components using Vue [Single File Components](https://v2.vuejs.org/v2/guide/single-file-components.html).
- Do not include unnecessary packages.
- Included packages should be backed by company or large community.
- Do not change the `package-lock.json` unless you have included or updated a package.
- Write meaningful log messages.
- All code is formatted according to our prettier configuration.
- No warnings occur during compilation.

### Backend Code

- Added functionality is tested by unit tests.
- Regular use cases are tested with integration tests.
- The existing package structure is not violated by the new code.
- Do not introduce dependency cycles.
- All code is formatted according to the eclipse buildin code style.
- No warnings occur during compilation.
- The OpenAPI documentation is consistent with the actual behavior of the software.
- Classes and public methods are uniformly documented using javadoc comments.
- Don't add comments to obvious things.
- If you need any of the features of objects, use them otherwise use primitives.
- Try to offload as much work as possible to the databases.
- Parallelize things only when needed (e.g. if more than 1000 objects will occur in a stream).
- Use Jakarta Bean Validation to validate user input.
