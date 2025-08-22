# Configuration

IceWheel Energy is configured through environment variables, which can also be set in the `application.yml` file. This
guide explains all the available configuration options.

When using Docker, you should create a `./data/application.yml` file and set your secrets there. When running without
Docker, it is recommended to use environment variables.

## Google OAuth2 (for User Login)

To enable user login with Google, you need to create OAuth2 credentials in the Google Cloud Console.

| Environment Variable | `application.yml` Path | Description | Default Value |
|----------------------|------------------------|-------------|---------------|
| `GOOGLE_CLIENT_ID`   | # Configuration        

IceWheel Energy is configured through environment variables, which can also be set in the `application.yml` file. This
guide explains all the available configuration options.

When using Docker, you should create a `./data/application.yml` file and set your secrets there. When running without
Docker, it is recommended to use environment variables.

## API Credential Setup

Before you can run the application, you must get API credentials from both Google and Tesla.

### Google SSO Setup

This application uses Google for login. You need to create an OAuth 2.0 Client ID to get a `client-id` and
`client-secret`.

1. **Go to the Google Cloud Console:
   ** [https://console.developers.google.com/apis/credentials](https://console.developers.google.com/apis/credentials)
2. Click **+ CREATE CREDENTIALS** at the top and select **OAuth client ID**.
3. For **Application type**, select **Web application**.
4. Give it a name, like "IceWheel Energy".
5. Under **Authorized JavaScript origins**, add the following URI:
    * `http://localhost:8081`
6. Under **Authorized redirect URIs**, add the following URI:
    * `http://localhost:8081/login/oauth2/code/google`
7. Click **CREATE**.
8. Copy the **Client ID** and **Client Secret**. You will need these for your `application.yml` file.

### Tesla API Setup

To allow the application to communicate with your Powerwall, you need to register an application in the Tesla Developer
portal.

1. **Go to the Tesla Developer Portal:** [https://developer.tesla.com/](https://developer.tesla.com/)
2. Log in with your Tesla account.
3. Click **Create Application**.
4. Fill in the application details.
5. For **Allowed Origin(s)**, add the following URI:
    * `http://localhost:8081`
6. For **Allowed Redirect URI(s)**, add the following URI:
    * `http://localhost:8081/api/tesla/fleet/auth/callback`
7. Once the application is created, you will get a **Client ID** and **Client Secret**. You will need these for your
   `application.yml` file.

**Important Note on Billing:** When you create developer accounts with Google and Tesla, you may be prompted to add a payment method. You can safely **skip this step**. IceWheel Energy is designed to use the free tiers of these services. For single-user, personal use, your API consumption will almost certainly fall within the free usage limits provided by both companies. For more details on Tesla's pricing, see the [official documentation](https://developer.tesla.com/docs/fleet-api/billing-and-limits).

**Disclaimer:** The API policies and pricing of both Google and Tesla are subject to change at any time. The developers of IceWheel Energy have no control over these external factors.

**Note:** If you deploy the application to a public domain, you will need to update these URIs in both the Google and
Tesla developer consoles to match your domain.

## Google OAuth2 (for User Login)

To enable user login with Google, you need to create OAuth2 credentials in the Google Cloud Console.

| Environment Variable   | `application.yml` Path                                     | Description                                           | Default Value     |
|------------------------|------------------------------------------------------------|-------------------------------------------------------|-------------------|
| `GOOGLE_CLIENT_ID`     | `security.oauth2.client.registration.google.client-id`     | The client ID for your Google OAuth2 application.     | `dummy-client-id` |
| `GOOGLE_CLIENT_SECRET` | `security.oauth2.client.registration.google.client-secret` | The client secret for your Google OAuth2 application. | `dummy-secret`    |

## Tesla API

To connect to the Tesla API, you need to provide your Tesla API credentials. These are not your Tesla account username
and password, but rather the client ID and secret for a registered Tesla API application.

| Environment Variable | `application.yml` Path | Description - `TESLA_CLIENT_ID`       | `tesla.client-id`      | The
client ID for your Tesla API application. - `TESLA_CLIENT_SECRET`   | `tesla.client-secret`  | The client secret for
your Tesla API application. - `TESLA_AUDIENCE`      | `tesla.audience`       | The audience for the Tesla API. This
should generally not be changed. - `TES_REDIRECT_URI`      | `tesla.redirect-uri`   | The redirect URI for your Tesla
API application. This must match the URI you configured in your Tesla developer account. The default value is
constructed from other properties. - `APP_DOMAIN`            | `tesla.domain`         | The domain where your
application is hosted. This is used to construct the `redirect-uri`. - `APP_PROTOCOL`          |
`app.protocol`         | The protocol to use for the `redirect-uri` (`http` or `https`). - `SESSION_COOKIE_SECURE` |
`server.servlet.session.cookie.secure` | Set to `true` in production when using HTTPS to ensure session cookies are sent
only over secure connections. - `https://fleet-api.prd.na.vn.cloud.tesla.com` |
| `localhost`        |
| `http`             |
| `false`            |

## Database

By default, the application uses an H2 file-based database. For production deployments, it is recommended to use
PostgreSQL.

### H2 (Default)

| Environment Variable | `application.yml` Path | Description                       | Default Value |
|----------------------|------------------------|-----------------------------------|---------------|
| `DB_USER`            | `datasource.username`  | The username for the H2 database. | `sa`          |
| `DB_PASSWORD`        | `datasource.password`  | The password for the H2 database. | `password`    |

### PostgreSQL

To use PostgreSQL, you need to activate the `postgres` Spring profile.

| Environment Variable                              | `application.yml` Path                                     | Description                                           | Default Value     |
|---------------------------------------------------|------------------------------------------------------------|-------------------------------------------------------|-------------------|
| `DB_HOST`                                         | `datasource.url`                                           | The hostname of the PostgreSQL server.                | `localhost`       |
| `DB_PORT`                                         | `datasource.url`                                           | The port of the PostgreSQL server.                    | `5432`            |
| `DB_NAME`                                         | `datasource.url`                                           | The name of the database.                             | `icewheel-energy` |
| `DB_USER`                                         | `datasource.username`                                      | The username for the PostgreSQL database.             | `postgres`        |
| `DB_PASSWORD`                                     | `datasource.password`                                      | The password for the PostgreSQL database.             | `example`         |
| The client ID for your Google OAuth2 application. | `dummy-client-id`                                          |
| `GOOGLE_CLIENT_SECRET`                            | `security.oauth2.client.registration.google.client-secret` | The client secret for your Google OAuth2 application. | `dummy-secret`    |

## Tesla API

To connect to the Tesla API, you need to provide your Tesla API credentials. These are not your Tesla account username
and password, but rather the client ID and secret for a registered Tesla API application.

| Environment Variable | `application.yml` Path | Description - `TESLA_CLIENT_ID`       | `tesla.client-id`      | The
client ID for your Tesla API application. - `TESLA_CLIENT_SECRET`   | `tesla.client-secret`  | The client secret for
your Tesla API application. - `TESLA_AUDIENCE`      | `tesla.audience`       | The audience for the Tesla API. This
should generally not be changed. - `TES_REDIRECT_URI`      | `tesla.redirect-uri`   | The redirect URI for your Tesla
API application. This must match the URI you configured in your Tesla developer account. The default value is
constructed from other properties. - `APP_DOMAIN`            | `tesla.domain`         | The domain where your
application is hosted. This is used to construct the `redirect-uri`. - `APP_PROTOCOL`          |
`app.protocol`         | The protocol to use for the `redirect-uri` (`http` or `https`). - `SESSION_COOKIE_SECURE` |
`server.servlet.session.cookie.secure` | Set to `true` in production when using HTTPS to ensure session cookies are sent
only over secure connections. - `https://fleet-api.prd.na.vn.cloud.tesla.com` |
| `localhost`        |
| `http`             |
| `false`            |

## Database

By default, the application uses an H2 file-based database. For production deployments, it is recommended to use
PostgreSQL.

### H2 (Default)

| Environment Variable | `application.yml` Path | Description                       | Default Value |
|----------------------|------------------------|-----------------------------------|---------------|
| `DB_USER`            | `datasource.username`  | The username for the H2 database. | `sa`          |
| `DB_PASSWORD`        | `datasource.password`  | The password for the H2 database. | `password`    |

### PostgreSQL

To use PostgreSQL, you need to activate the `postgres` Spring profile.

| Environment Variable | `application.yml` Path | Description                               | Default Value     |
|----------------------|------------------------|-------------------------------------------|-------------------|
| `DB_HOST`            | `datasource.url`       | The hostname of the PostgreSQL server.    | `localhost`       |
| `DB_PORT`            | `datasource.url`       | The port of the PostgreSQL server.        | `5432`            |
| `DB_NAME`            | `datasource.url`       | The name of the database.                 | `icewheel-energy` |
| `DB_USER`            | `datasource.username`  | The username for the PostgreSQL database. | `postgres`        |
| `DB_PASSWORD`        | `datasource.password`  | The password for the PostgreSQL database. | `example`         |
