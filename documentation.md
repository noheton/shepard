# Documentation concept

## Location of the documentation

The documentation is located in the project [Documentation](https://gitlab.com/dlr-shepard/documentation) in the Wiki area.
There it can be read and written intuitively.
Furthermore this project contains some selected sample projects for interaction with shepard as well as instructions for installation and maintenance of shepard for administrators.
Thus, the documentation of the entire shepard infrastructure is completely in one place.

Source code projects contain no in-depth documentation other than a `README.md` file with a short textual description.
This description contains instructions for setting up a development environment as well as very short documentation aimed at developers.

The [Architecture](https://gitlab.com/dlr-shepard/architecture) project contains discussions about the further development of shepard in the form of issues and merge requests.
Documentation of important architectural concepts is stored in the repository in the form of markdown files.
In this way, the concepts can be worked out together.

## Target groups

### Users of the frontend

The web frontend is largely self-explanatory and is not documented further.

### Users of the API

The API is described automatically via [OpenAPI](https://www.openapis.org/).
A Swagger UI presents this documentation in a graphical format.
Selected sample projects in [Documentation](https://gitlab.com/dlr-shepard/documentation) serve as tutorials for users.

### Administrators

Installation and maintenance of shepard is described in the project [Deployment](https://gitlab.com/dlr-shepard/deployment).
In addition to instructions, there are also configuration files with examples.

### Backend/Frontend developers

Developers can find short instructions in the respective `README.md` files.
Further documentation exists in the form of comments and possibly automatically generated documentation (Javadoc, Doxygen, Swing, etc.).

## How to use the instruction documents

The wiki pages can be directly edited by authorized persons.
These documents always refer to the current version of shepard.
Among other things, the following aspects are described here:

- The system architecture
- The data model
- The REST API
- The user authentication
- The use of the web frontend
- OpenAPI clients
- The integration of new databases in the backend

## Use of the architecture documents

Comments, errors and planned changes are documented in issues.
Initial discussions to find a solution can also take place there.
As soon as a solution becomes apparent, a merge request is opened with the planned change.
This serves as a basis for discussion.
As soon as the merge request has been accepted by at least one other person, the changes are transferred to the master branch, the documentation is updated, and the merge request and the issue are closed.
From this point on, the document is valid and will be implemented.
