# FlowPay

FlowPay is a small payment-gateway simulator built with Spring Boot and Java 21. I made it to explore what happens around a payment: a merchant creates an order, a customer checks out, a bank response changes the payment state, and the merchant's ledger and webhook events are updated.

It is a learning and portfolio project. It does not connect to Razorpay, a bank, or a card network, and it never moves real money.

## What you can try

- Register a demo merchant and create orders.
- Open a hosted-style checkout page and simulate success, failure, delayed success, or timeout.
- See payment activity, merchant balance, and ledger entries in the dashboard.
- Search and filter payments by ID, order, status, method, date, and amount.
- Create partial or full refunds, and settle the simulated available balance.
- Send signed payment, refund, and settlement webhook events to a receiver you configure.
- Run a reconciliation check to compare successful payments with ledger credits.

## Start FlowPay

### Run locally

Install Java 21, open a terminal in this project directory, and run:

```sh
./mvnw spring-boot:run
```

Then open [http://localhost:8080](http://localhost:8080). The default profile uses an in-memory H2 database. It is the quickest way to explore the app, but its data disappears when FlowPay stops.

### Run with Docker Compose

Install Docker Desktop, make sure it is running, then run:

```sh
docker compose up --build
```

Open [http://localhost:8080](http://localhost:8080). Compose starts FlowPay and PostgreSQL; PostgreSQL keeps demo data in a Docker volume when the containers stop.

To stop the services and keep the data:

```sh
docker compose down
```

To remove the services **and erase the saved PostgreSQL demo data**:

```sh
docker compose down -v
```

The default Compose database password is only for a personal local demo. Set `DB_PASSWORD` in your shell or an untracked `.env` file before using this setup beyond your own machine. Never commit passwords or API secrets.

### Run the image from Docker Hub

After publishing the image to your Docker Hub account (`arjunsingh63/flowpay`), pull and run it with:

```sh
docker pull arjunsingh63/flowpay:latest
docker run --rm -p 8080:8080 arjunsingh63/flowpay:latest
```

Open [http://localhost:8080](http://localhost:8080). This standalone image uses the default in-memory database, so its data is temporary. Use Docker Compose when you want PostgreSQL to keep the demo data.

To build and publish a multi-architecture image from the project directory:

```sh
docker login
docker buildx build \
  --platform linux/amd64,linux/arm64 \
  -t arjunsingh63/flowpay:latest \
  --push .
```

Docker Hub login will ask you to authenticate. Do not put a password or access token in the image, Dockerfile, or source code. The multi-architecture image supports common Intel/AMD and ARM Linux container environments; macOS and Windows users need Docker Desktop to run Linux containers.

## A first payment, step by step

1. In the dashboard, register a demo merchant. FlowPay saves the API key and webhook signing secret in that browser's local storage. Keep both private.
2. Create an order and enter the amount in rupees.
3. Click **Checkout link** beside the order. Open the link and choose a fake payment method and scenario, or use the dashboard's **Quick payment** form.
4. For a successful payment, FlowPay records a simulated fee (2% of the order, rounded up), credits the remaining amount to the merchant ledger, and updates the available balance.
5. Return to the dashboard to view the payment and ledger entry. Use the filters to find a payment. You can refund a successful payment from its row or settle the available balance from the Settlements panel.
6. Click **Run check** to compare successful payments with their ledger credits.

The checkout link uses a random token that expires after 30 minutes. A customer opening the link does not need the merchant API key.

### Payment scenarios

| Scenario | What you should see |
|---|---|
| Success | Payment succeeds immediately; ledger and balance are credited. |
| Failure | Payment fails; no merchant balance is credited. |
| Delayed success | Payment stays pending for about 8 seconds, then succeeds. |
| Timeout | Payment stays pending for about 20 seconds, then fails. |

## Try an outbound webhook

FlowPay saves an event in its database and a background job sends it as an HTTP `POST` to the merchant's saved receiver URL. The repository includes a tiny Python receiver that prints the event and checks its signature. For a local test:

1. Open a terminal in the project directory and run `python3 docs/webhook_receiver.py`. Paste the merchant's webhook signing secret at the prompt; it will not be displayed as you type. Leave this terminal open.
2. Start FlowPay locally in another terminal with `./mvnw spring-boot:run` if it is not already running.
3. In the FlowPay dashboard, set the receiver URL to `http://127.0.0.1:3000/webhooks` and save it **before** creating the payment.
4. Create an order and make a simulated successful payment. Check the Python terminal for `Signature valid: True` and the dashboard's **Webhook deliveries** panel for **DELIVERED**.

Opening the receiver URL in a browser sends a `GET`; that only confirms the receiver is running. FlowPay sends webhook events using `POST` after an event occurs.

FlowPay sends these headers:

| Header | Meaning |
|---|---|
| `X-FlowPay-Event` | Event type, such as `payment.succeeded` or `refund.created`. |
| `X-FlowPay-Event-Id` | Event identifier. Use it to deduplicate retries. |
| `X-FlowPay-Delivery-Id` | Identifier for this delivery record. |
| `X-FlowPay-Signature` | HMAC-SHA256 signature of the exact raw request body. |

The signature format is `sha256=` followed by lowercase hexadecimal. Verify it with the merchant's webhook signing secret using the raw request body, and compare signatures in constant time. The signing secret is shown in the credentials dialog when the merchant is created. Don't share it or commit it.

Any `2xx` response marks a delivery successful. Network errors and non-`2xx` responses are retried after 15 seconds, 1 minute, 5 minutes, and 15 minutes (up to five attempts in total). The dashboard offers a manual retry after an event is marked failed. Delivery is at least once, so webhook receivers should deduplicate by event ID.

Receiver hosts are restricted by the exact-host `WEBHOOK_HOST` allowlist. It defaults to `localhost,127.0.0.1`, which permits the local test above when FlowPay runs directly on your computer. To use another receiver, add its hostname to `WEBHOOK_HOST` and restart FlowPay. For example:

```sh
WEBHOOK_HOST=localhost,127.0.0.1,webhook.site ./mvnw spring-boot:run
```

Only add destinations you intend to receive FlowPay event data. With Docker Compose, configure the allowlist in your shell or `.env` file; a receiver running in another container should be addressed by its Compose service name, which must also be allowed.

## API overview

All amounts in the REST API are integer paise: `12500` means ₹125.00. Merchant routes require the `X-Api-Key` header, except registration. Public checkout routes use their checkout token instead. Payment, refund, and settlement mutations require an `Idempotency-Key` header.

| Method | Route | Purpose |
|---|---|---|
| `POST` | `/api/merchants` | Register a demo merchant and issue credentials. |
| `GET`, `PUT` | `/api/merchants/me` | View or update merchant and webhook receiver settings. |
| `POST`, `GET` | `/api/orders` | Create or list orders. |
| `GET` | `/api/orders/{id}` | View an order. |
| `POST` | `/api/checkout-sessions` | Create a time-limited checkout link. |
| `GET` | `/api/public/checkout/{token}` | Read public checkout details. |
| `POST` | `/api/public/checkout/{token}/pay` | Start a simulated checkout payment. |
| `POST` | `/api/public/checkout/{token}/refresh` | Refresh checkout status. |
| `POST` | `/api/payments` | Create an idempotent payment attempt. |
| `POST` | `/api/payments/{id}/process` | Process the simulated bank outcome. |
| `POST` | `/api/payments/{id}/check` | Refresh a pending payment. |
| `GET` | `/api/transactions` | List payments; supports `status`, `from`, and `to` filters. |
| `GET` | `/api/transactions/ledger` | List merchant ledger entries. |
| `POST`, `GET` | `/api/refunds` | Create or list refunds. |
| `POST`, `GET` | `/api/settlements` | Settle available balance or list settlements. |
| `GET` | `/api/webhooks` | Inspect webhook delivery records. |
| `POST` | `/api/webhooks/{id}/retry` | Retry a failed delivery. |
| `POST` | `/api/reconciliation/run` | Compare payments with ledger entries. |
| `GET` | `/api/reconciliation/issues` | List reconciliation issues; optionally filter by `status=OPEN`. |
| `GET` | `/actuator/health` | Check application health. |

For request and response examples, see [`docs/openapi.yaml`](docs/openapi.yaml).

## Configuration

| Variable | Default | What it changes |
|---|---|---|
| `DB_URL` | H2 in-memory URL | Database connection URL for a non-Compose run. |
| `DB_USERNAME`, `DB_PASSWORD` | `sa`, empty | Database credentials for a non-Compose run. |
| `APP_PUBLIC_BASE_URL` | `http://localhost:8080` | Base URL used in customer checkout links. |
| `WEBHOOK_HOST` | `localhost,127.0.0.1` | Comma-separated exact host allowlist for webhook receivers. |
| `WEBHOOK_DELIVERY_INTERVAL_MS` | `5000` | How often the delivery worker checks for queued webhooks. |

The default local profile uses H2 and Hibernate schema updates. Docker Compose selects the PostgreSQL profile, which uses the migrations under `src/main/resources/db/migration`.

## Tests

Run the backend test suite with:

```sh
./mvnw test
```

The tests cover simulated bank outcomes, fees, payment idempotency, hosted checkout, merchant balance credit, and event/webhook persistence.

## What this project is—and isn't

FlowPay is useful for learning and demonstrating payment flows, API design, idempotency, ledger updates, reconciliation, and webhook delivery. It is not a payment processor and does not accept real payments or perform real settlements. It also lacks the security, compliance, monitoring, and operational controls required for production financial software.
