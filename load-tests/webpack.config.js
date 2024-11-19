const path = require("path");
const fs = require("fs");

/**
 * Function to dynamically generate entries for bundles by traversing the '/src' folder and fining all .ts files.
 * The idea behind this is to have one bundle entry for every written test file.
 * The load test files therefore shall not depend on each other.
 *
 * All files in the 'utils' folder are ignored and are not created as bundle entries.
 * Use the 'utils' directory to provide helper functions and vars used in the tests.
 *
 *
 * This function assumes the following folder structure:
 * src
 * ├── collections
 * │   └── test.ts
 * ├── timeseries
 * │   └── test.ts
 * └── utils
 *    ├── helper1.ts
 *    └── helper2.ts
 * @param dir - entrypoint dir to start building the entries object
 * @returns Entries Map<string, string>
 */
function generateEntries(dir) {
  const entries = {};

  function readDirRecursive(directory, functionPathName) {
    fs.readdirSync(directory).forEach((file) => {
      const fullPath = path.join(directory, file);
      const extname = path.extname(file);
      const basename = path.basename(file, extname);

      const stat = fs.statSync(fullPath);
      if (stat.isDirectory()) {
        // dont traverse the utils directory
        if (!fullPath.includes("utils")) {
          readDirRecursive(fullPath, basename);
        }
      } else if (extname === ".ts") {
        const relativePath = path.relative(dir, fullPath).replaceAll(".ts", "");
        entries[relativePath] = "./" + fullPath;
      }
    });
  }
  readDirRecursive(dir, "");
  return entries;
}

const entries = generateEntries("./src");

module.exports = {
  mode: "production",
  entry: entries,
  output: {
    path: path.resolve(__dirname, "dist"), // eslint-disable-line
    libraryTarget: "commonjs",
    filename: "[name].bundle.js",
  },
  resolve: {
    extensions: [".tsx", ".ts", ".js"],
  },
  module: {
    rules: [
      {
        test: /\.tsx?$/,
        use: "ts-loader",
        exclude: /node_modules/,
      },
    ],
  },
  target: "web",
  externals: /k6(\/.*)?/,
};
