# Nuxt Frontend

Look at the [Nuxt 3 documentation](https://nuxt.com/docs/getting-started/introduction) to learn more.

#### Downloads

- Node JS and NPM: <https://nodejs.org/en/>
- Vue CLI: <https://cli.vuejs.org/>
- Visual Studio Code: <https://code.visualstudio.com/>
- Vue Devtools for Firefox: <https://addons.mozilla.org/en-US/firefox/addon/vue-js-devtools/>
- Vue Devtools for Chrome: <https://chrome.google.com/webstore/detail/vuejs-devtools/nhdogjmejiglipccpnnnanhbledajbpd>

#### Installation

- install Node JS and NPM
- install Visual Studio Code and the recommended plugins

## Setup

- Make sure to install the dependencies:

```bash
# npm
npm install

# pnpm
pnpm install

# yarn
yarn install

# bun
bun install
```

- Add the needed environment variables by copying `env.example` into `.env` and adapting the values:

```bash
NUXT_PUBLIC_BACKEND_API_URL='Backend API URL' # (should end with '/')
AUTH_ORIGIN='Frontend URL' # (should end with '/')
NUXT_AUTH_SECRET='Frontend auth secret'
NUXT_OIDC_CLIENT_ID='oidc-client-id'
NUXT_OIDC_ISSUER='oidc-issuer-url'
```

> **_NOTE:_** The `NUXT_AUTH_SECRET` could be any random generated string which will be used to hash JWT tokens. You can quickly create a good value on the command line using `openssl`.
>
> ```bash
> $ openssl rand -base64 32
> ```

## Development Server

Start the development server on `http://localhost:3000`:

```bash
# npm
npm run dev

# pnpm
pnpm run dev

# yarn
yarn dev

# bun
bun run dev
```

## Production

Build the application for production:

```bash
# npm
npm run build

# pnpm
pnpm run build

# yarn
yarn build

# bun
bun run build
```

Locally preview production build:

```bash
# npm
npm run preview

# pnpm
pnpm run preview

# yarn
yarn preview

# bun
bun run preview
```

Check out the [deployment documentation](https://nuxt.com/docs/getting-started/deployment) for more information.
