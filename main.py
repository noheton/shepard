import sys
from datetime import datetime
from typing import List, Tuple

import jinja2
from gitlab.client import Gitlab
from gitlab.v4.objects.merge_requests import MergeRequest
from gitlab.v4.objects.projects import Project

BREAKING_CHANGE_LABEL = "Breaking Change"
DEPENDENCY_BUMP_LABEL = "dependencies"
TEMPLATE_FILE = "release_notes.md"
TOKEN_FILE = "token.txt"

GITLAB_INSTANCE = "https://gitlab.com"
PROJECTS = {"backend": 27250272, "frontend": 27250279}


def get_changes(
    project: Project, merged_after: str
) -> Tuple[List[MergeRequest], List[MergeRequest], List[MergeRequest]]:
    breaking_changes: List[MergeRequest] = []
    dependency_changes: List[MergeRequest] = []
    other_changes: List[MergeRequest] = []

    merge_requests = project.mergerequests.list(
        state="merged",
        order_by="updated_at",
        updated_after=merged_after,
        target_branch=project.default_branch,
    )

    for merge_request in merge_requests:
        if merge_request.merged_at < merged_after:
            continue
        if BREAKING_CHANGE_LABEL in merge_request.labels:
            breaking_changes.append(merge_request)
        elif DEPENDENCY_BUMP_LABEL in merge_request.labels:
            dependency_changes.append(merge_request)
        else:
            other_changes.append(merge_request)

    return (breaking_changes, dependency_changes, other_changes)


def get_mr_list(merge_requests: List[MergeRequest]) -> str:
    result = ""
    for merge_request in merge_requests:
        if len(result) > 0:
            result = result + "\n"
        result = result + f"- {merge_request.title} ({merge_request.web_url})"
    return result


def get_release_notes(
    description: str,
    breaking_changes: List[MergeRequest],
    other_changes: List[MergeRequest],
) -> str:
    template_loader = jinja2.FileSystemLoader(searchpath="./")
    template_env = jinja2.Environment(loader=template_loader)
    template = template_env.get_template(TEMPLATE_FILE)
    result = template.render(
        description=description,
        breaking_changes=get_mr_list(breaking_changes),
        other_changes=get_mr_list(other_changes),
    )
    return result


def get_release_tag() -> str:
    now = datetime.now()
    return now.strftime("%Y.%m.%d-release")


def create_release() -> int:
    token = ""
    with open(TOKEN_FILE, "r", encoding="UTF-8") as token_file:
        token = token_file.read()
    instance = Gitlab(GITLAB_INSTANCE, token)

    project_name = input("Project: ")
    try:
        project = instance.projects.get(PROJECTS[project_name])
    except KeyError as ex:
        print(f"Project {ex} could not be found")
        return 1

    latest_release = project.releases.list()[0]
    breaking, _dependencies, others = get_changes(project, latest_release.released_at)
    release_tag = get_release_tag()

    print({project.name_with_namespace})
    print("Merge Requests:")
    print(*[mr.title for mr in breaking + others], sep="\n")
    print()
    title = input("Release title: ")
    description = input("Description: ")
    release_notes = get_release_notes(description, breaking, others)

    print(f"Title: {title}")
    print(f"Tag: {release_tag}")
    print("Release notes:")
    print(release_notes)
    print()

    approve = input("OK? (y/N) ")
    if approve != "y":
        return 1

    project.releases.create(
        {
            "name": title,
            "tag_name": release_tag,
            "description": release_notes,
            "ref": project.default_branch,
        }
    )
    print("Successfully released")
    return 0


if __name__ == "__main__":
    sys.exit(create_release())
