import { readFileSync } from "fs";
import * as ts from "typescript";

export function getExportsForSourceFile(sourceFile: ts.SourceFile) {
  const allExports: string[] = [];

  visitNode(sourceFile);

  function visitNode(node: ts.Node) {
    if (ts.isExportSpecifier(node)) {
      const name = node.name.getText();
      console.log("export specifier", name);
      allExports.push(name);
    } else if (node.kind === ts.SyntaxKind.ExportKeyword) {
      const parent = node.parent;
      if (ts.isFunctionDeclaration(parent) || ts.isTypeAliasDeclaration(parent) || ts.isInterfaceDeclaration(parent)) {
        const name = parent!.name!.getText();
        console.log("export function", name);
        allExports.push(name);
      } else if (ts.isVariableStatement(parent)) {
        parent.declarationList.declarations.forEach((declaration) => {
          const name = declaration.name.getText();
          console.log("export var", name);
          allExports.push(name);
        });
      }
    }

    ts.forEachChild(node, visitNode);
  }

  return allExports;
}

const fileNames = process.argv.slice(2);
console.log(`File names: ${fileNames}`);

fileNames.forEach((fileName) => {
  // Parse a file
  const sourceFile = ts.createSourceFile(
    fileName,
    readFileSync(fileName).toString(),
    ts.ScriptTarget.ES2015,
    /*setParentNodes */ true,
  );

  const allExports = getExportsForSourceFile(sourceFile);
  console.log(allExports);
});
