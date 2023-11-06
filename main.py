import sys

from src.releases import create_release

GITLAB_INSTANCE = "https://gitlab.com"
PROJECTS = {"backend": 27250272, "frontend": 27250279}
TOKEN_FILE = "token.txt"  # noqa: S105


if __name__ == "__main__":
    TOKEN = ""
    with open(TOKEN_FILE, encoding="UTF-8") as token_file:
        TOKEN = token_file.read()

    project_name = input("Project: ")
    if project_name not in PROJECTS:
        print(f"Project ID {project_name} is not known")
        sys.exit(1)

    sys.exit(create_release(GITLAB_INSTANCE, TOKEN, PROJECTS[project_name]))
