import sys
from datetime import datetime
from typing import List, Tuple

import jinja2
from gitlab.client import Gitlab
from gitlab.v4.objects.merge_requests import MergeRequest
from gitlab.v4.objects.projects import Project

BREAKING_CHANGE_LABEL = "Breaking Change"
TEMPLATE_FILE = "release_notes.md"
TOKEN_FILE = "token.txt"

PROJECTS = {"backend": 27250272, "frontend": 27250279}


def get_changes(
    project: Project, merged_after: str
) -> Tuple[List[MergeRequest], List[MergeRequest]]:
    breaking_changes: List[MergeRequest] = []
    other_changes: List[MergeRequest] = []

    mrs = project.mergerequests.list(
        state="merged",
        order_by="updated_at",
        updated_after=merged_after,
        target_branch=project.default_branch,
    )

    for mr in mrs:
        if mr.merged_at < merged_after:
            continue
        if BREAKING_CHANGE_LABEL in mr.labels:
            breaking_changes.append(mr)
        else:
            other_changes.append(mr)

    return (breaking_changes, other_changes)


def get_mr_list(mrs: List[MergeRequest]) -> str:
    result = ""
    for mr in mrs:
        if len(result) > 0:
            result = result + "\n"
        result = result + f"- {mr.title} ({mr.web_url})"
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


if __name__ == "__main__":
    token = ""
    with open(TOKEN_FILE, "r") as f:
        token = f.read()
    instance = Gitlab("https://gitlab.com", token)

    project_name = input("Project: ")
    try:
        project = instance.projects.get(PROJECTS[project_name])
    except KeyError as ex:
        print(f"Project {ex} could not be found")
        sys.exit(1)

    latest_release = project.releases.list()[0]
    breaking, others = get_changes(project, latest_release.released_at)
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
        sys.exit(1)

    release = project.releases.create(
        {
            "name": title,
            "tag_name": release_tag,
            "description": release_notes,
            "ref": project.default_branch,
        }
    )
    print("Successfully released")
    sys.exit(0)
