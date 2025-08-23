# IceWheel Energy

## 🚀 Try the Live Demo!

Want to see IceWheel Energy in action without installing anything? We've got you covered!

**[Click here to access the live demo](https://energy.icewheel.dev/)**

This is a development environment, so it's a great way to explore the features and see how the application works. However, please keep in mind:

*   **It's not stable for long-term use.**
*   **Data may be reset or deleted weekly.**

We encourage you to try it out, and when you're ready for a stable, private instance, you can follow the instructions below to self-host.

**P.S.** If the site doesn't open, it means we're working on it! We're constantly testing new features and improvements, so check back soon.

---

IceWheel Energy is a self-hosted application that gives you full control over your Tesla Powerwall charging schedule. By
running your own instance, you can manage your Powerwall's behavior without relying on third-party services, ensuring
your energy data remains private.

This application allows you to create sophisticated schedules to optimize your Powerwall's charging and discharging
cycles based on your electricity tariff (e.g., on-peak and off-peak hours).

**For full documentation, please see the [docs](./docs/index.md) directory.**

## Quick Start

1.  **Clone the repository:**
    ```bash
    git clone https://github.com/icewheel/icewheel-energy.git
    cd icewheel-energy
    ```

2.  **Configure your environment:**
    Create a `.env` file by copying the `.env.example` file. Then, edit the `.env` file to provide the necessary credentials.
    ```bash
    cp .env.example .env
    nano .env
    ```

3.  **Run the application:**
    ```bash
    docker-compose up -d
    ```

The application will be available at `http://localhost:8081` (or the port you specified in your `.env` file).

## Configuration

Configuration is managed through environment variables. You can set these variables in a `.env` file in the project root.

### Environment Variables

| Variable              | Description                                                                                                                                                                 | Default Value       |
| --------------------- | --------------------------------------------------------------------------------------------------------------------------------------------------------------------------- | ------------------- |
| `APP_PORT`            | The port on which the application will be accessible.                                                                                                                       | `8081`              |
| `DB_NAME`             | The name of the PostgreSQL database.                                                                                                                                        | `icewheel-energy`   |
| `DB_USER`             | The username for the PostgreSQL database.                                                                                                                                   | `postgres`          |
| `DB_PASSWORD`         | The password for the PostgreSQL database.                                                                                                                                   | `example`           |
| `GOOGLE_CLIENT_ID`    | The client ID for your Google OAuth2 application. See [Google API Setup](./docs/getting-started/configuration.md#google-sso-setup) for details.                               |
| `GOOGLE_CLIENT_SECRET`| The client secret for your Google OAuth2 application. See [Google API Setup](./docs/getting-started/configuration.md#google-sso-setup) for details.                             |
| `TESLA_CLIENT_ID`     | The client ID for your Tesla API application. See [Tesla API Setup](./docs/getting-started/configuration.md#tesla-api-setup) for details.                                     |
| `TESLA_CLIENT_SECRET` | The client secret for your Tesla API application. See [Tesla API Setup](./docs/getting-started/configuration.md#tesla-api-setup) for details.                                   |
| `TESLA_REDIRECT_URI`  | The redirect URI for your Tesla API application. This must match the URI you configured in your Tesla developer account.                                                      | `http://localhost:8081/api/tesla/fleet/auth/callback` |
| `APP_DOMAIN`          | The domain where your application is hosted. This is used to construct the `redirect-uri`.                                                                                | `localhost`         |
| `APP_PROTOCOL`        | The protocol to use for the `redirect-uri` (`http` or `https`).                                                                                                            | `http`              |
| `SESSION_COOKIE_SECURE`| Set to `true` in production when using HTTPS to ensure session cookies are sent only over secure connections.                                                              | `false`             |

**Note:** When setting up your Tesla and Google developer accounts, you can skip adding a payment method. The application uses free-tier APIs, so your account will still work without payment information. However, please be aware that both Google and Tesla may change their API pricing and policies at any time, and the developers of this application have no control over these changes.

## License

This project is licensed under the GNU General Public License v3.0. See the [LICENSE](./LICENSE) file for details.