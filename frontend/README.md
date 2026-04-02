# Nuxt Frontend

Look at the [Nuxt 3 documentation](https://nuxt.com/docs/getting-started/introduction) to learn more.

## Frontend Development Environment

### Downloads

- Node JS and NPM: <https://nodejs.org/en/>
- Vue CLI: <https://cli.vuejs.org/>
- Visual Studio Code: <https://code.visualstudio.com/>
- Volar: <https://marketplace.visualstudio.com/items?itemName=Vue.volar>
- Vue Devtools for Firefox: <https://addons.mozilla.org/en-US/firefox/addon/vue-js-devtools/>
- Vue Devtools for Chrome: <https://chrome.google.com/webstore/detail/vuejs-devtools/nhdogjmejiglipccpnnnanhbledajbpd>

### Installation

- install Node JS and NPM
- install Vue CLI `npm install -g @vue/cli`
- install Visual Studio Code and the recommended plugins
- clone Git repository
- `cd` into the frontend folder
- open command line, type in `npm install .`

### Local backend

If you don't have a working backend available, you can find a description of how to implement a local backend [here](#backend)

### Cerate a valid .env file

Add the needed environment variables by copying `.env.example` in the frontend folder and rename it to `.env`. After that, adapt all necessary variables with correct values:

```bash
NUXT_PUBLIC_BACKEND_API_URL='Backend API URL' # (should end with '/')
AUTH_ORIGIN='Frontend URL' # (should end with '/')
NUXT_AUTH_SECRET='Frontend auth secret'
NUXT_OIDC_CLIENT_ID='oidc-client-id'
NUXT_OIDC_ISSUER='oidc-issuer-url'
```

Example Values

```bash
NUXT_PUBLIC_BACKEND_API_URL='http://localhost:8080/shepard/api/'
AUTH_ORIGIN='http://localhost:3000/api/auth/'
NUXT_AUTH_SECRET='MyOwnSecret'
NUXT_OIDC_CLIENT_ID='frontend-dev'
NUXT_OIDC_ISSUER='http://localhost:8082/realms/master/'
```

> **_NOTE:_** The `NUXT_AUTH_SECRET` could be any random generated string which will be used to hash JWT tokens. You can quickly create a good value on the command line using `openssl`.
>
> ```bash
> $ openssl rand -base64 32
> ```

## Setup and Run

Make sure to install the dependencies:

```bash
npm install
```

start the project:

```bash
npm run start:frontend
```

### Development Server

Start the development server on `http://localhost:3000`:

```bash
npm run dev
```

### Production

Build the application for production:

```bash
npm run build
```

Locally preview production build:

```bash
npm run preview
```

Check out the [deployment documentation](https://nuxt.com/docs/getting-started/deployment) for more information.
