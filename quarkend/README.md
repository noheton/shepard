# quarkend

> **Experimental project:** We are in the process of migrating the backend under `backend/` to use quarkus in this directory.

This is the new backend based on Quarkus.

If you want to learn more about Quarkus, please visit its website: <https://quarkus.io/>.

## CONTRIBUTING to Quarkend (Setup)

### Backend

#### Downloads

- OpenJDK 17 (LTS): <https://adoptium.net/index.html?variant=openjdk17&jvmVariant=hotspot>
- One of the following IDEs (VSCode is recommended):
  - VSCode: <https://code.visualstudio.com/download>
  - IntelliJ: <https://www.jetbrains.com/idea/download/>

#### Installation

1. install OpenJDK
2. install VSCode or IntelliJ
3. clone Git repository
4. Add Project to IDE:
   - IntelliJ:
     1. In the "Project Structure" settings go to "Modules" (or press F4).
     2. Click "+" and "Import Module".
     3. Select the "quarkend" folder.
     4. Select "Import Module from External Model".
     5. Select "Maven" and create.
     6. Click ok to save the new module.
     7. Wait for indexing of files.
     8. Afterwards you can use the existing run configuration on the top right to run quarkus.
   - VSCode:
     1. Install the recommended extensions in `.vscode/extensions.json` (VSCode should already suggest it)
     2. The "Debug Quarkus" configuration under "Run and Debug" in the left-side bar should start a working Quarkus instance

#### First run

- IntelliJ: use the existing run configuration on the top right to run quarkus.
- VSCode: The "Debug Quarkus" configuration under "Run and Debug" in the left-side bar should start a working Quarkus instance

> **Warning:** Using the the VSCode run configuration "Debug Quarkus" above an opened file can edit the run configuration and cause issues. Stick to the "Run and Debug" tab on the left side to avoid this.
