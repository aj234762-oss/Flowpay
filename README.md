# FlowPay

FlowPay is a local payment-gateway simulator built with Spring Boot, React, PostgreSQL, and Kafka. It combines a merchant dashboard, payment API, simulated checkout, ledger, settlements, reconciliation, and signed webhook delivery in one project.

FlowPay is for learning and local development. It does not connect to a real bank or move real money.

## Run FlowPay with Docker Hub images

The repository includes a Docker Compose file that starts the complete application. It pulls the FlowPay service images from [`arjunsingh63/flowpay`](https://hub.docker.com/r/arjunsingh63/flowpay), and also pulls PostgreSQL and Kafka.

Clone this GitHub repository to get the Compose file, then pull and start the containers:

```sh
git clone https://github.com/aj234762-oss/Flowpay.git
cd Flowpay
docker compose pull
docker compose up -d --no-build
```

The Compose file uses these FlowPay image tags:

- `arjunsingh63/flowpay:razor-v2-backend`
- `arjunsingh63/flowpay:razor-v2-frontend`
- `arjunsingh63/flowpay:razor-v2-receiver`

These image tags must be available in Docker Hub before the pull commands will work.

## Run by building from GitHub source

If you prefer to build the project images on your computer, clone the repository and run Compose with the build option:

```sh
git clone https://github.com/aj234762-oss/Flowpay.git
cd Flowpay
docker compose up --build -d
```

You need Docker Desktop with Docker Compose installed and running.

## Open the application

| Service | Address |
| --- | --- |
| Merchant dashboard | <http://localhost:5173> |
| Webhook inbox | <http://localhost:8081> |
| Backend health | <http://localhost:8080/actuator/health> |

Check container status with:

```sh
docker compose ps
```

Stop the services and keep the database:

```sh
docker compose down
```

Stop the services and delete the saved database and demo data:

```sh
docker compose down -v
```

## How the project works

1. A merchant creates an order in the dashboard.
2. Checkout simulates a payment outcome, such as success, failure, delayed success, or timeout.
3. The backend saves payment and ledger records in PostgreSQL.
4. Payment, refund, and settlement events pass through Kafka to the webhook delivery worker.
5. The worker sends signed HTTP requests to the merchant's webhook URL. Delivery status appears in the dashboard; received requests appear in the webhook inbox.

## First use

1. Open the dashboard and register a merchant using an email and password.
2. Save the API key and webhook signing secret shown after registration. They are shown once. Use the email and password to sign in; use the API key for integrations.
3. In **Settings**, set the webhook URL to `http://receiver:8081/webhooks/flowpay` when using this Docker Compose setup.
4. Open <http://localhost:8081>, enter that merchant's webhook signing secret, and save it. The local receiver keeps the secret in memory, so enter it again after restarting the receiver.
5. Create an order, open its checkout, and choose a simulated payment result.

## Generate or replace credentials

Merchant registration automatically creates an API key and a webhook signing secret. FlowPay shows both once during registration, so save them somewhere private.

If you need a replacement, sign in as the merchant owner and open **Settings → Credential security**:

1. Choose **Make a new API key** or **Make a new webhook secret**.
2. Confirm your dashboard password.
3. Copy the replacement when it appears; it is shown only once.

An API key is for merchant API requests; it is not the dashboard password and does not go in the receiver. Replacing it immediately invalidates the old API key, so update any integrations using it.

The webhook receiver runs as the Compose `receiver` service on port **8081**. Start the app with the Docker Compose command above, then open the receiver inbox at <http://localhost:8081>. Paste the webhook signing secret into the **Signing secret** field and click **Save secret**. When running in Compose, the merchant's webhook URL in **Settings** should be `http://receiver:8081/webhooks/flowpay`; `receiver` is the service name and `8081` is its port.

If you replace the webhook secret, immediately save the new value in the receiver inbox. The old secret will no longer verify signatures. The receiver stores the secret in memory, so re-enter it after the receiver restarts.

## Dashboard guide

- **Orders:** Create orders and open checkout.
- **Transactions:** Review payment status and amounts.
- **Merchant ledger:** See payment credits and refund or settlement debits under **Transactions**.
- **Refunds:** Create full or partial refunds for eligible successful payments.
- **Settlements:** Settle the available simulated balance to the mock merchant bank.
- **Webhooks:** Review event delivery attempts and retry failures.
- **Reconciliation:** Run a check to compare successful payments with their ledger credits and review any mismatches.
- **Settings:** Manage merchant information, webhook configuration, team access, and credential rotation.

## Webhook receiver

The receiver service uses port 8081. The page at <http://localhost:8081> is the local webhook inbox. The backend sends HTTP `POST` requests to `/webhooks/flowpay`; opening that path in a browser sends a `GET` and does not trigger a webhook.

When running through Compose, configure the merchant webhook URL as `http://receiver:8081/webhooks/flowpay`. The hostname `receiver` lets the backend container reach the receiver container. Enter the signing secret in the inbox page, not in the URL. Webhooks use HMAC-SHA256 signatures and may be retried, so a receiver should deduplicate events by event ID.

More about webhook headers and verification: [`docs/webhooks.md`](docs/webhooks.md).

## Tests and API reference

Run backend tests:

```sh
bash mvnw test
```

Build the frontend:

```sh
cd frontend
npm ci
npm run build
```

API contract: [`docs/openapi.yaml`](docs/openapi.yaml). API amounts are integer paise; for example, `12500` means ₹125.00. Merchant API requests use an `X-Api-Key` header, and create requests use an `Idempotency-Key` header.

## Local configuration

Compose uses a demo database password. For a fresh setup, you can override it in an untracked `.env` file beside `compose.yaml` by setting `DB_PASSWORD=your-local-password`. Changing the value does not change the password in an already initialized database volume. Do not commit `.env` files, passwords, API keys, or webhook secrets.
