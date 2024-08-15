# clients/tests/typescript

This folder contains a node.js script checking if there are differences between the exports of the typescript shepard client specified in the package.json and the client available
It is intended to be run in a pipeline job.
There, the generated client is already available as build artifact.

## Running it locally

> **NOTE:** You need docker installed locally to generate the typescript client

- run `npm install`
- run `npm run compare-exports:with-client-generation`

## Investigating found differences

If there are exports missing in the new client, we need to inform users about these changes.
If there is no other new feature already adding the relevant explanations to the release notes, we need to investigate why and how the client changed.

For that purpose you can use the `src/compareExports.ts` script as a playground to interact with the old object and generate the client to check out the new files.
