# Connecting Your Accounts

To use IceWheel Energy, you need to connect two accounts: your Google account (for logging in) and your Tesla account (
for managing your Powerwall).

## 1. Logging in with Google

IceWheel Energy uses Google for authentication. This means you don't need to create a new username and password for this
application. Simply click the "Login with Google" button on the homepage.

This process uses OAuth2, which is a secure standard. The application will only request basic profile information (your
email address and name) to identify you. It does not get access to your Google password or any other private data in
your Google account.

## 2. Connecting Your Tesla Account

After you have logged in, you will be prompted to connect your Tesla account. This is a one-time process that authorizes
the application to access your Powerwall's data and send commands to it.

### How it Works

The application uses the official Tesla Fleet API, which is the modern and secure way for third-party applications to
interact with Tesla products. When you connect your account, you will be redirected to Tesla's official website to log
in and approve the connection.

**Important**: Your Tesla username and password are never entered into or stored by the IceWheel Energy application. You
provide them directly to Tesla, who then gives the application a secure token (like a limited-access key) to communicate
with your Powerwall.

This token allows the application to:

* Read your Powerwall's status (e.g., current charge level, backup reserve percentage).
* Send commands to change the backup reserve percentage according to your schedules.

This process ensures that your Tesla credentials remain secure and you can revoke the application's access at any time
from your Tesla account settings.

Once both accounts are connected, you can start creating schedules to manage your Powerwall.
