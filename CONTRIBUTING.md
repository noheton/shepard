# Contributing to shepard

First off, thanks for taking time to contribute!
The following is a set of guidelines for contributing to the shepard frontend.
These are mostly guidelines, not rules.
Use your best judgment, and feel free to propose changes to this document in a merge request.

When contributing to this repository, we differentiate between two contributions.
Small changes and larger contributions.
If you want to make a small change, you can create a merge request right away.
If you want to add a new feature or make major changes to the codebase, we should discuss it either [here](https://gitlab.com/dlr-shepard/frontend/-/issues) (changes affecting only the frontend) or [here](https://gitlab.com/dlr-shepard/architecture/-/issues) (changes affecting the entire infrastructure).

Please note we have a code of conduct, please follow it in all your interactions with the project.

## Table of Contents

- [Code of Conduct](#code-of-conduct)
- [I don't want to read this whole thing I just have a question!](#i-dont-want-to-read-this-whole-thing-i-just-have-a-question)
- [Useful Links](#useful-links)
- [Setting Up a Developing Environment](#setting-up-a-developing-environment)
- [How Can I Contribute?](#how-can-i-contribute)
- [Code Review Checklist](#code-review-checklist)

## Code of Conduct

Everyone participating in this project is asked to conduct themselves in a reasonable and proper manner.
All interactions should be respectful and friendly, as is appropriate in a professional setting.
By participating, you are accepting this CoC.

## I don't want to read this whole thing I just have a question!

Please don't file an issue to ask a question.
You'll get faster results by contacting us on [Mattermost at HZDR](https://mattermost.hzdr.de/signup_user_complete/?id=f5ycfi3nmigixxaerhpdg6q66y)

## Useful Links

- [Frontend Documentation](https://gitlab.com/dlr-shepard/documentation/-/wikis/Frontend)
- [Vue.js Documentation](https://v2.vuejs.org/v2/guide/)
- [BootstrapVue](https://bootstrap-vue.org/)
- [Material Design Icons](https://materialdesignicons.com/)

## Setting Up a Developing Environment

### Downloads

- Node JS and NPM: <https://nodejs.org/en/>
- Visual Studio Code: <https://code.visualstudio.com/>
- Vetur: <https://marketplace.visualstudio.com/items?itemName=octref.vetur>
- Vue Devtools for Firefox: <https://addons.mozilla.org/en-US/firefox/addon/vue-js-devtools/>
- Vue Devtools for Chrome: <https://chrome.google.com/webstore/detail/vuejs-devtools/nhdogjmejiglipccpnnnanhbledajbpd>

### Installation

- install Node JS
- install Visual Studio Code and the Vetur Plugin
- checkout the repository
- open comand line, type in `npm install .`

### Local backend

If you don't have a working backend available, you can find a description of how to implement a local backend [here](https://gitlab.com/dlr-shepard/backend/-/blob/master/CONTRIBUTING.md#setting-up-a-developing-environment)

### First run

- fill in the variables in your environment file [.env.development](.env.development)
- start the project: `npm run serve`

## How Can I Contribute?

### Issue Boards and Labels

We use issue [labels](https://docs.gitlab.com/ee/user/project/labels.html) and [boards](https://docs.gitlab.com/ee/user/project/issue_board.html) to manage ongoing issues.
The templates for issues and merge requests usually take care of setting the necessary labels, so you don't have to do that.
Everything else is managed by our maintainers.

### Bug Reports

This section guides you through submitting a bug report for the shepard frontend.
Following these guidelines helps maintainers and the community understand your report, reproduce the behavior, and find related reports.

Bugs are tracked as GitLab issues.
When you are creating a bug report, please include as many details as possible.
After you've determined which repository your bug is related to, create an issue on that repository and provide the following information by filling in this [template](.gitlab/issue_templates/Bug.md).
This will help us to resolve issues faster.
The template can be selected when creating a new issue.

> **Note:** If you find a **Closed** issue that seems like it is the same thing that you're experiencing, open a new issue and include a link to the original issue in the body of your new one.

### Feature Request

This section guides you through submitting a feature request for the shepard frontend, including completely new features and minor improvements to existing functionality.
Following these guidelines helps maintainers and the community understand your suggestion.

Feature requests are tracked as GitLab issues.
When you are creating a feature request, please include as many details as possible.
After you've determined which repository your featrue request is related to, create an issue on that repository and provide the following information by filling in this [template](.gitlab/issue_templates/Feature.md).
Explain the requested feature and include additional details to help maintainers understand your request.
This will help us to resolve issues faster.
As before, the template can be selected when creating a new issue.

### Your First Code Contribution

Unsure where to begin contributing to shepard frontend? You can start by looking through these [beginner issues](https://gitlab.com/dlr-shepard/frontend/-/issues?scope=all&state=opened&label_name[]=beginner)

### Merge Request Process

This section guides you through submitting a merge request for the shepard frontend.
Following these guidelines helps maintainers to review your merge request faster.

First, create a new branch.
You can create a branch within the frontend repository if you have the necessary permissions.
If not, you can always fork the repository and create a new branch there.
Now you are ready to begin with your contribution.

Once you have created an initial prototype of your contribution, commit your changes to GitLab and open a merge request with a meaningful name.
Open up your merge request as soon as possible and mark it as [draft](https://docs.gitlab.com/ee/user/project/merge_requests/drafts.html) so everyone knows you are working on an issue.
This prevents duplicate work and unhappy contributors.
Please fill in the required [template](.gitlab/merge_request_templates/default.md) and follow the [code review checklist](#code-review-checklist) below.
As before with issues, the template can be selected when creating a new merge request.

After your first commit, you can add as many additional commits as you like.
After your contribution is accepted, your changes will be squashed into one commit anyway.

While you are working on your contribution, others may merge their merge request.
To keep up with current developments, it is a good idea to occasionally [rebase](https://docs.gitlab.com/ee/topics/git/git_rebase.html#git-rebase) your branch to the current master branch.

## Code Review Checklist

- The code on the main branch is always functional.
- There is no commented out code for the main branch.
- Changes in merge requests are complete, self-contained, and implement only a single functionality.
- Changes that do not belong to an existing merge request are implemented in a separate merge request.
- The existing package structure is not violated by the new code.
- Use the latest JavaScript standard.
- Define components using Vue [Single File Components](https://v2.vuejs.org/v2/guide/single-file-components.html).
- Do not include unnecassary packages.
- Included packages should be backed by company or large community.
- Do not change the `package-lock.json` unless you have included or updated a package.
- Write meaningful log messages.
- All code is formatted according to our prettier configuration.
- No warnings occur during compilation.
