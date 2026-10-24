# FlowPay local webhook receiver

This small, dependency-free Node.js app receives FlowPay webhook POSTs, verifies the `X-FlowPay-Signature` HMAC-SHA256 header, and displays deliveries in a local inbox.

## Run it

From the `Razor` project folder:

```bash
cd receiver
npm start
```

Keep this terminal running. Open <http://localhost:8081> to view the inbox.

## Connect FlowPay

1. In the inbox, paste the merchant's **webhook signing secret** (shown when the merchant was registered) and select **Save secret**. The receiver keeps it in memory only; enter it again after restarting the receiver.
2. In FlowPay **Settings**, set the Webhook URL to `http://localhost:8081/webhooks/flowpay` and save.
3. In FlowPay **Webhooks**, retry any failed events, or create a new simulated payment/refund.

The inbox shows the event ID, received time, payload, and signature verification result. FlowPay's Webhooks page shows delivery status. The receiver binds only to the local loopback interfaces and stores inbox entries in memory, so both are cleared when the receiver process stops.

Requires Node.js 18 or newer. No `npm install` is needed.
