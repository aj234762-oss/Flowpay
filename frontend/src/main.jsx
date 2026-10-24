import React, { useEffect, useState, useRef } from "react";
import { createRoot } from "react-dom/client";
import {
  ArrowUpRight,
  ArrowDownLeft,
  ArrowRight,
  Activity,
  LayoutDashboard,
  ShoppingBag,
  CreditCard,
  RotateCcw,
  Landmark,
  Webhook,
  Settings,
  Plus,
  ChevronRight,
  Check,
  Copy,
  X,
  LogIn,
  RefreshCw,
  FlaskConical,
  ShieldCheck,
  LogOut,
  Wallet,
  Clock,
  AlertCircle,
} from "lucide-react";
import { request, newKey, money, date, paise } from "./api";
import "./style.css";

const nav = [
  ["Overview", LayoutDashboard],
  ["Orders", ShoppingBag],
  ["Transactions", CreditCard],
  ["Refunds", RotateCcw],
  ["Settlements", Landmark],
  ["Webhooks", Webhook],
  ["Reconciliation", Activity],
  ["Settings", Settings],
];
const emptyData = {
  merchant: null,
  orders: [],
  transactions: [],
  ledger: [],
  refunds: [],
  settlements: [],
  webhooks: [],
  reconciliationIssues: [],
  users: [],
};
const Badge = ({ value }) => (
  <span className={"badge " + value?.toLowerCase()}>
    <i />
    {value?.replaceAll("_", " ")}
  </span>
);
const Empty = ({
  text = "No records yet. Create an order to get started.",
}) => (
  <div className="empty">
    <Activity size={25} />
    <p>{text}</p>
  </div>
);
const Short = ({ id }) => (
  <span className="mono" title={id}>
    {id?.slice(0, 15)}…
  </span>
);
function Modal({ title, children, close }) {
  return (
    <div className="overlay" onClick={close}>
      <section
        className="modal"
        role="dialog"
        aria-modal="true"
        aria-label={title}
        onClick={(e) => e.stopPropagation()}
      >
        <header>
          <h2>{title}</h2>
          <button className="icon" aria-label="Close dialog" onClick={close}>
            <X size={20} />
          </button>
        </header>
        {children}
      </section>
    </div>
  );
}

function App() {
  const [key, setKey] = useState(
    () => sessionStorage.getItem("flowpay-key") || "",
  );
  const [apiKey, setApiKey] = useState(
    () => sessionStorage.getItem("flowpay-api-key") || "",
  );
  const [authMode, setAuthMode] = useState(
    () => sessionStorage.getItem("flowpay-auth-mode") ||
      (sessionStorage.getItem("flowpay-key")?.startsWith("fpsess_") ? "password" : "apiKey"),
  );
  const [page, setPage] = useState("Overview");
  const [data, setData] = useState(emptyData);
  const [error, setError] = useState("");
  const [notice, setNotice] = useState("");
  const [busy, setBusy] = useState(false);
  const [loading, setLoading] = useState(true);
  const [modal, setModal] = useState(null);
  const [selected, setSelected] = useState(null);
  const [credentialResult, setCredentialResult] = useState(null);
  const [checkout, setCheckout] = useState(null);
  const [result, setResult] = useState(null);
  const [filter, setFilter] = useState({ status: "", from: "", to: "" });
  const [filtered, setFiltered] = useState(null);
  const [reconciliationReport, setReconciliationReport] = useState(null);
  const generation = useRef(0);
  const [formKey, setFormKey] = useState(newKey());
  async function refresh(silent = false) {
    if (!key) {
      setLoading(false);
      return;
    }
    const version = ++generation.current;
    try {
      const values = await Promise.all(
        [
          "/merchants/me",
          "/orders",
          "/transactions",
          "/transactions/ledger",
          "/refunds",
          "/settlements",
          "/webhooks",
          "/reconciliation/issues",
          "/users",
        ].map((p) => request(key, p)),
      );
      if (version === generation.current) {
        setData(
          Object.fromEntries(
            [
              "merchant",
              "orders",
              "transactions",
              "ledger",
              "refunds",
              "settlements",
              "webhooks",
              "reconciliationIssues",
              "users",
            ].map((n, i) => [n, values[i]]),
          ),
        );
        if (!silent) setError("");
      }
    } catch (e) {
      if (version === generation.current) {
        if (e.status === 401) {
          sessionStorage.removeItem("flowpay-key");
          sessionStorage.removeItem("flowpay-auth-mode");
          setKey("");
          setAuthMode("apiKey");
          setData(emptyData);
          setError(
            "Your sign-in has expired. Sign in again to continue.",
          );
        } else {
          setError(e.message);
        }
      }
    } finally {
      if (version === generation.current) setLoading(false);
    }
  }
  useEffect(() => {
    setLoading(true);
    setFiltered(null);
    setCheckout(null);
    setResult(null);
    setModal(null);
    if (key) sessionStorage.setItem("flowpay-key", key);
    else sessionStorage.removeItem("flowpay-key");
    refresh();
    return () => {
      generation.current++;
    };
  }, [key]);
  useEffect(() => {
    const timer = setInterval(() => refresh(true), 5000);
    return () => clearInterval(timer);
  }, [key]);
  useEffect(() => {
    if (!result) return;
    const updated = data.transactions.find((p) => p.id === result.id);
    if (updated) setResult(updated);
  }, [data.transactions]);
  useEffect(() => {
    if (notice) {
      const timer = setTimeout(() => setNotice(""), 5000);
      return () => clearTimeout(timer);
    }
  }, [notice]);
  async function runReconciliation() {
    await act(async () => {
      const report = await request(key, "/reconciliation/run", { method: "POST" });
      setReconciliationReport(report);
      setNotice(`Reconciliation checked ${report.paymentsChecked} payments and found ${report.issuesFound} open issues.`);
    });
  }
  async function copyHostedCheckoutLink(order) {
    await act(async () => {
      const created = await request(key, "/checkout-sessions", {
        method: "POST",
        body: { orderId: order.id },
      });
      await navigator.clipboard.writeText(created.checkoutUrl);
      setNotice("Hosted checkout link copied. It expires in 30 minutes.");
    });
  }
  async function act(fn) {
    if (busy) return;
    setBusy(true);
    setError("");
    try {
      await fn();
      await refresh(true);
    } catch (e) {
      setError(e.message);
    } finally {
      setBusy(false);
    }
  }
  function open(type, record = null) {
    setFormKey(newKey());
    setSelected(record);
    if (type === "rotateApiKey" || type === "rotateWebhookSecret") {
      setCredentialResult(null);
    }
    setModal(type);
    setError("");
  }

  function rotateCredential(type, password) {
    act(async () => {
      const route = type === "API key"
        ? "/auth/credentials/api-key/rotate"
        : "/auth/credentials/webhook-secret/rotate";
      const response = await request(key, route, {
        method: "POST",
        body: { password },
      });
      setCredentialResult({ type, value: response.value });
      setModal("rotatedCredential");
    });
  }
  function signOut() {
    const currentToken = key;
    if (currentToken) request(currentToken, "/auth/logout", { method: "POST" }).catch(() => {});
    setKey("");
    sessionStorage.removeItem("flowpay-auth-mode");
    setAuthMode("apiKey");
    setPage("Overview");
    setError("");
    setSelected(null);
    setCheckout(null);
    setResult(null);
    setModal(null);
    setData(emptyData);
  }
  const successful = data.transactions.filter((p) => p.status === "SUCCESS");
  const gross = successful.reduce((s, p) => s + p.amount, 0);
  const refundTotal = data.refunds.reduce((s, r) => s + r.amount, 0);
  const completed = data.transactions.filter((p) =>
    ["SUCCESS", "FAILED"].includes(p.status),
  );
  const rate = completed.length
    ? Math.round((successful.length / completed.length) * 100)
    : 0;
  const merchant = data.merchant;
  const isViewer = merchant?.role === "VIEWER";
  const changePage = (p) => {
    setPage(p);
    setCheckout(null);
    setResult(null);
    setFiltered(null);
  };
  const beginCheckout = (o) => {
    setCheckout(o);
    setResult(
      data.transactions.find(
        (p) =>
          p.orderId === o.id &&
          ["CREATED", "PENDING", "SUCCESS"].includes(p.status),
      ) || null,
    );
    setFormKey(newKey());
    setPage("Checkout");
  };
  const transactions = filtered ?? data.transactions;
  function PaymentTable({ rows }) {
    return rows.length ? (
      <div className="table-wrap">
        <table>
          <thead>
            <tr>
              <th>Payment</th>
              <th>Method</th>
              <th>Date & time</th>
              <th>Status</th>
              <th className="right">Amount</th>
              <th />
            </tr>
          </thead>
          <tbody>
            {rows.map((p) => (
              <tr
                key={p.id}
                onClick={() => open("detail", p)}
                className="clickable"
              >
                <td>
                  <span className="table-icon">
                    <ArrowDownLeft size={17} />
                  </span>
                  <Short id={p.id} />
                </td>
                <td>{p.method}</td>
                <td className="muted">{date(p.createdAt)}</td>
                <td>
                  <Badge value={p.status} />
                </td>
                <td className="right strong">{money(p.amount)}</td>
                <td>
                  <button aria-label={"View " + p.id} className="icon">
                    <ChevronRight size={16} />
                  </button>
                </td>
              </tr>
            ))}
          </tbody>
        </table>
      </div>
    ) : (
      <Empty />
    );
  }
  return (
    <div className="app-shell">
      <aside className="sidebar">
        <a
          className="brand"
          href="#"
          onClick={(e) => {
            e.preventDefault();
            changePage("Overview");
          }}
        >
          <span className="brand-symbol">
            f<span>↗</span>
          </span>
          flowpay<span className="brand-dot">.</span>
        </a>
        <div className="workspace">
          <span className="avatar">{merchant?.name?.slice(0, 1) || "F"}</span>
          <div>
            <strong>{merchant?.name || "Merchant workspace"}</strong>
            <small>Merchant account</small>
          </div>
          <ShieldCheck size={16} />
        </div>
        <p className="nav-label">WORKSPACE</p>
        <nav>
          {nav.map(([name, Icon]) => (
            <button
              key={name}
              className={page === name ? "active" : ""}
              onClick={() => changePage(name)}
            >
              <Icon size={18} />
              {name}
              {name === "Webhooks" &&
                data.webhooks.some((w) => w.status === "FAILED") && (
                  <span className="nav-dot" />
                )}
            </button>
          ))}
        </nav>
        <div className="sidebar-bottom">
          <div className="sandbox-card">
            <FlaskConical size={20} />
            <strong>A safe place to build.</strong>
            <p>Every payment here is simulated. Experiment freely.</p>
            <span>
              WORKSHOP EDITION <ArrowUpRight size={13} />
            </span>
          </div>
          <button className="account-button" onClick={() => open("connect")}>
            <LogIn size={16} /> Switch merchant
          </button>
          <button className="account-button" onClick={signOut}>
            <LogOut size={16} /> Sign out
          </button>
          <small className="version">FLOWPAY / V1.0</small>
        </div>
      </aside>
      <div className="main-shell">
        <header className="topbar">
          <div className="breadcrumb">
            Workspace <ChevronRight size={14} /> <strong>{page}</strong>
          </div>
          <div className="topbar-right">
            {merchant ? (
              <button
                className="topbar-signout"
                aria-label="Sign out"
                title="Sign out"
                onClick={signOut}
              >
                <LogOut size={17} />
                <span>Sign out</span>
              </button>
            ) : (
              <button
                className="topbar-signout"
                onClick={() => open("connect")}
              >
                <LogIn size={17} />
                <span>Sign in</span>
              </button>
            )}
            <span className="test-mode">
              <i /> TEST MODE
            </span>
            <span className="separator" />
            <span className="avatar small">
              {merchant?.name?.slice(0, 1) || "F"}
            </span>
          </div>
        </header>
        <main>
          {error && (
            <div className="alert" role="alert">
              <AlertCircle size={18} />
              <span>{error}</span>
              <button onClick={() => setError("")} aria-label="Dismiss error">
                <X size={16} />
              </button>
            </div>
          )}
          {notice && (
            <div className="toast" role="status">
              <Check size={17} />
              {notice}
            </div>
          )}
          {loading ? (
            <div className="empty">Connecting to your workspace…</div>
          ) : !merchant ? (
            <section className="panel welcome">
              <h1>Connect your merchant account</h1>
              <p>
                Create a merchant account or sign in with your email and password.
              </p>
              <button className="primary" onClick={() => open("register")}>
                Create merchant
              </button>
              <button className="secondary" onClick={() => open("connect")}>
                Sign in
              </button>
              <button className="secondary" onClick={() => refresh()}>
                Retry connection
              </button>
            </section>
          ) : (
            <>
              <div className="page-heading">
                <div>
                  <p className="eyebrow">YOUR BUSINESS, IN FLOW</p>
                  <h1>
                    {page === "Overview"
                      ? "Your money. A clearer picture."
                      : page === "Checkout"
                        ? "Make a test payment"
                        : page}
                  </h1>
                  <p>
                    {
                      {
                        Overview:
                          "A little clarity for every payment, refund, and settlement.",
                        Orders:
                          "Create an order, then take it through a simulated checkout.",
                        Transactions:
                          "Every payment attempt, with the details that matter.",
                        Refunds:
                          "Return a little, or return it all. Keep every refund accounted for.",
                        Settlements:
                          "Move your available balance to a simulated bank account.",
                        Webhooks:
                          "Follow each event from your gateway to your merchant endpoint.",
                        Settings:
                          "Your business details and developer connection.",
                        Checkout:
                          "Choose a payment method and a mock bank outcome.",
                        Reconciliation:
                          "Check successful payments against the merchant ledger.",
                      }[page]
                    }
                  </p>
                </div>
                {page !== "Checkout" && (
                  <button className="primary" disabled={isViewer} onClick={() => open("order")}>
                    <Plus size={17} />
                    Create order
                  </button>
                )}
              </div>
              {page === "Overview" && (
                <>
                  <div className="overview-grid">
                    <section className="balance-card">
                      <div>
                        <span className="card-eyebrow">AVAILABLE BALANCE</span>
                        <Wallet size={20} />
                      </div>
                      <h2>{money(merchant.balance)}</h2>
                      <p>Ready for your next settlement</p>
                      <div className="balance-bottom">
                        <span>
                          <i /> All amounts in INR
                        </span>
                        <button onClick={() => changePage("Settlements")}>
                          Settle funds <ArrowUpRight size={17} />
                        </button>
                      </div>
                      <div className="balance-art" />
                    </section>
                    <section className="stat-card">
                      <span className="stat-icon">
                        <ArrowDownLeft size={20} />
                      </span>
                      <p>Total collected</p>
                      <h2>{money(gross)}</h2>
                      <span className="stat-foot">
                        {successful.length} successful payments
                      </span>
                    </section>
                    <section className="stat-card">
                      <span className="stat-icon lilac">
                        <Activity size={20} />
                      </span>
                      <p>Payment success</p>
                      <h2>
                        {rate}
                        <small>%</small>
                      </h2>
                      <span className="stat-foot">
                        Across {completed.length} completed attempts
                      </span>
                    </section>
                  </div>
                  <div className="middle-grid">
                    <section className="panel volume">
                      <div className="panel-heading">
                        <div>
                          <h3>Payment activity</h3>
                          <p>Successful collections · last 7 days</p>
                        </div>
                        <span className="subtle-tag">INR</span>
                      </div>
                      <div className="chart">
                        {Array.from({ length: 7 }, (_, i) => {
                          const day = new Date();
                          day.setDate(day.getDate() - 6 + i);
                          const amount = successful
                            .filter(
                              (p) =>
                                new Date(p.createdAt).toDateString() ===
                                day.toDateString(),
                            )
                            .reduce((s, p) => s + p.amount, 0);
                          return (
                            <div className="bar-column" key={i}>
                              <span>{amount ? money(amount) : "—"}</span>
                              <div className="bar-track">
                                <div
                                  className="bar"
                                  style={{
                                    height: amount
                                      ? Math.max(
                                          8,
                                          (amount / Math.max(gross, 1)) * 100,
                                        ) + "%"
                                      : "3px",
                                  }}
                                />
                              </div>
                              <small>
                                {day.toLocaleDateString("en-IN", {
                                  weekday: "short",
                                })}
                              </small>
                            </div>
                          );
                        })}
                      </div>
                    </section>
                    <section className="panel quick">
                      <span className="quick-icon">
                        <FlaskConical size={23} />
                      </span>
                      <h3>
                        Build confidence.
                        <br />
                        One test at a time.
                      </h3>
                      <p>
                        Try a successful UPI payment, a bank timeout, or a
                        delayed response.
                      </p>
                      <button onClick={() => changePage("Orders")}>
                        Open the simulator <ArrowRight size={17} />
                      </button>
                      <div className="quick-footer">
                        <ShieldCheck size={15} /> No real money moves.
                      </div>
                    </section>
                  </div>
                  <section className="panel">
                    <div className="panel-heading">
                      <div>
                        <h3>Recent transactions</h3>
                        <p>Your latest payment activity</p>
                      </div>
                      <button
                        className="text-button"
                        onClick={() => changePage("Transactions")}
                      >
                        View all <ArrowRight size={16} />
                      </button>
                    </div>
                    <PaymentTable rows={data.transactions.slice(0, 5)} />
                  </section>
                </>
              )}
              {page === "Orders" && (
                <section className="panel">
                  <div className="panel-heading">
                    <h3>
                      All orders{" "}
                      <span className="count">{data.orders.length}</span>
                    </h3>
                    <span className="muted">
                      INR · amounts include all charges
                    </span>
                  </div>
                  {data.orders.length ? (
                    <div className="table-wrap">
                      <table>
                        <thead>
                          <tr>
                            <th>Order / receipt</th>
                            <th>Created</th>
                            <th>Status</th>
                            <th className="right">Amount</th>
                            <th />
                          </tr>
                        </thead>
                        <tbody>
                          {data.orders.map((o) => (
                            <tr key={o.id}>
                              <td>
                                <strong>{o.receipt}</strong>
                                <small className="block">
                                  <Short id={o.id} />
                                </small>
                              </td>
                              <td>{date(o.createdAt)}</td>
                              <td>
                                <Badge value={o.status} />
                              </td>
                              <td className="right strong">
                                {money(o.amount)}
                              </td>
                              <td>
                                <div className="order-actions">
                                  <button
                                    className="secondary compact"
                                    disabled={o.status === "PAID" || isViewer}
                                    onClick={() => beginCheckout(o)}
                                  >
                                    Simulate <ArrowUpRight size={14} />
                                  </button>
                                  <button
                                    className="secondary compact"
                                    disabled={o.status === "PAID" || busy || isViewer}
                                    onClick={() => copyHostedCheckoutLink(o)}
                                  >
                                    Copy link <Copy size={14} />
                                  </button>
                                </div>
                              </td>
                            </tr>
                          ))}
                        </tbody>
                      </table>
                    </div>
                  ) : (
                    <Empty />
                  )}
                </section>
              )}
              {page === "Transactions" && (
                <section className="panel">
                  <form
                    className="filters"
                    onSubmit={(e) => {
                      e.preventDefault();
                      act(async () => {
                        const q = new URLSearchParams(
                          Object.entries(filter).filter(([, v]) => v),
                        );
                        setFiltered(await request(key, "/transactions?" + q));
                      });
                    }}
                  >
                    <label>
                      Status
                      <select
                        value={filter.status}
                        onChange={(e) =>
                          setFilter({ ...filter, status: e.target.value })
                        }
                      >
                        <option value="">All statuses</option>
                        {["CREATED", "PENDING", "SUCCESS", "FAILED"].map(
                          (v) => (
                            <option key={v}>{v}</option>
                          ),
                        )}
                      </select>
                    </label>
                    <label>
                      From (UTC)
                      <input
                        type="date"
                        value={filter.from}
                        onChange={(e) =>
                          setFilter({ ...filter, from: e.target.value })
                        }
                      />
                    </label>
                    <label>
                      To (UTC)
                      <input
                        type="date"
                        value={filter.to}
                        onChange={(e) =>
                          setFilter({ ...filter, to: e.target.value })
                        }
                      />
                    </label>
                    <button className="secondary" disabled={busy}>
                      Apply filters
                    </button>
                    <button
                      type="button"
                      className="text-button"
                      onClick={() => {
                        setFilter({ status: "", from: "", to: "" });
                        setFiltered(null);
                      }}
                    >
                      Reset
                    </button>
                  </form>
                  <PaymentTable rows={transactions} />
                  <div className="panel-heading ledger-heading">
                    <div>
                      <h3>Merchant ledger</h3>
                      <p className="muted">Balance changes from payments, refunds, and settlements</p>
                    </div>
                  </div>
                  {data.ledger.length ? (
                    <div className="table-wrap">
                      <table>
                        <thead>
                          <tr>
                            <th>Entry</th>
                            <th>Reference</th>
                            <th>Date &amp; time</th>
                            <th className="right">Amount</th>
                            <th className="right">Balance after</th>
                          </tr>
                        </thead>
                        <tbody>
                          {data.ledger.map((entry) => (
                            <tr key={entry.id}>
                              <td><Badge value={entry.type} /></td>
                              <td><Short id={entry.referenceId} /></td>
                              <td>{date(entry.createdAt)}</td>
                              <td className="right strong">{money(entry.amount)}</td>
                              <td className="right strong">{money(entry.balanceAfter)}</td>
                            </tr>
                          ))}
                        </tbody>
                      </table>
                    </div>
                  ) : (
                    <Empty text="Ledger entries appear after a payment, refund, or settlement." />
                  )}
                </section>
              )}
              {page === "Refunds" && (
                <>
                  <div className="summary-strip">
                    <div>
                      <p>Total refunded</p>
                      <h2>{money(refundTotal)}</h2>
                    </div>
                    <div>
                      <p>Completed refunds</p>
                      <h2>{data.refunds.length}</h2>
                    </div>
                    <button className="primary" disabled={isViewer} onClick={() => open("refund")}>
                      Issue refund <RotateCcw size={16} />
                    </button>
                  </div>
                  <section className="panel">
                    <div className="panel-heading">
                      <h3>Refund history</h3>
                      <span className="muted">Full and partial refunds</span>
                    </div>
                    {data.refunds.length ? (
                      <div className="table-wrap">
                        <table>
                          <thead>
                            <tr>
                              <th>Refund</th>
                              <th>Payment</th>
                              <th>Created</th>
                              <th>Status</th>
                              <th className="right">Amount</th>
                            </tr>
                          </thead>
                          <tbody>
                            {data.refunds.map((r) => (
                              <tr key={r.id}>
                                <td>
                                  <Short id={r.id} />
                                </td>
                                <td>
                                  <Short id={r.paymentId} />
                                </td>
                                <td>{date(r.createdAt)}</td>
                                <td>
                                  <Badge value={r.status} />
                                </td>
                                <td className="right">{money(r.amount)}</td>
                              </tr>
                            ))}
                          </tbody>
                        </table>
                      </div>
                    ) : (
                      <Empty text="No refunds yet. Successful payments can be refunded here." />
                    )}
                  </section>
                </>
              )}
              {page === "Settlements" && (
                <>
                  <div className="settlement-banner">
                    <div>
                      <p className="card-eyebrow">READY TO SETTLE</p>
                      <h2>{money(merchant.balance)}</h2>
                      <p>
                        Processing fee: 2%, rounded up to the nearest paise.
                      </p>
                    </div>
                    <button
                      className="light-button"
                      disabled={!merchant.balance || busy || isViewer}
                      onClick={() => open("settle")}
                    >
                      Settle available balance <ArrowUpRight size={17} />
                    </button>
                  </div>
                  <section className="panel">
                    <div className="panel-heading">
                      <h3>Settlement history</h3>
                      <span className="subtle-tag">SIMULATED TRANSFERS</span>
                    </div>
                    {data.settlements.length ? (
                      <div className="table-wrap">
                        <table>
                          <thead>
                            <tr>
                              <th>Settlement</th>
                              <th>Created</th>
                              <th>Destination</th>
                              <th>Status</th>
                              <th className="right">Amount</th>
                            </tr>
                          </thead>
                          <tbody>
                            {data.settlements.map((s) => (
                              <tr key={s.id}>
                                <td>
                                  <Short id={s.id} />
                                </td>
                                <td>{date(s.createdAt)}</td>
                                <td>Mock merchant bank</td>
                                <td>
                                  <Badge value={s.status} />
                                </td>
                                <td className="right strong">
                                  {money(s.amount)}
                                </td>
                              </tr>
                            ))}
                          </tbody>
                        </table>
                      </div>
                    ) : (
                      <Empty text="Your first settlement will appear here." />
                    )}
                  </section>
                </>
              )}
              {page === "Reconciliation" && (
                <>
                  <div className="summary-strip">
                    <div>
                      <p>Payments checked</p>
                      <h2>{reconciliationReport?.paymentsChecked ?? "—"}</h2>
                    </div>
                    <div>
                      <p>Open issues found</p>
                      <h2>{reconciliationReport?.issuesFound ?? data.reconciliationIssues.filter((issue) => issue.status === "OPEN").length}</h2>
                    </div>
                    <button className="primary" disabled={busy || isViewer} onClick={runReconciliation}>
                      Run check <Activity size={16} />
                    </button>
                  </div>
                  {reconciliationReport?.checkedAt && (
                    <p className="muted">Last manual check: {date(reconciliationReport.checkedAt)}</p>
                  )}
                  <section className="panel">
                    <div className="panel-heading">
                      <h3>Payment and ledger issues</h3>
                      <span className="muted">Successful payments should have one matching net ledger credit.</span>
                    </div>
                    {data.reconciliationIssues.length ? (
                      <div className="table-wrap">
                        <table>
                          <thead>
                            <tr><th>Payment</th><th>Issue</th><th>Status</th><th>Expected</th><th>Actual</th><th>Details</th></tr>
                          </thead>
                          <tbody>
                            {data.reconciliationIssues.map((issue) => (
                              <tr key={issue.id}>
                                <td><Short id={issue.referenceId} /></td>
                                <td>{issue.issueType.replaceAll("_", " ")}</td>
                                <td><Badge value={issue.status} /></td>
                                <td>{issue.expectedAmount == null ? "—" : money(issue.expectedAmount)}</td>
                                <td>{issue.actualAmount == null ? "—" : money(issue.actualAmount)}</td>
                                <td>{issue.details}</td>
                              </tr>
                            ))}
                          </tbody>
                        </table>
                      </div>
                    ) : (
                      <Empty text="No mismatches found. Run a check to compare your payments and ledger." />
                    )}
                  </section>
                </>
              )}
              {page === "Webhooks" && (
                <>
                  <div className="info-banner">
                    <Webhook size={20} />
                    <span>
                      Endpoint:{" "}
                      <strong>{merchant.webhookUrl || "Not configured"}</strong>
                      <small>
                        Signed with HMAC-SHA256 · up to 5 delivery attempts
                      </small>
                    </span>
                    <button
                      className="text-button"
                      onClick={() => changePage("Settings")}
                    >
                      Configure <ArrowRight size={16} />
                    </button>
                  </div>
                  <section className="panel">
                    <div className="panel-heading">
                      <h3>Delivery history</h3>
                      <button className="text-button" onClick={() => refresh()}>
                        <RefreshCw size={15} /> Refresh
                      </button>
                    </div>
                    {data.webhooks.length ? (
                      <div className="table-wrap">
                        <table>
                          <thead>
                            <tr>
                              <th>Event</th>
                              <th>Status</th>
                              <th>Attempts</th>
                              <th>HTTP</th>
                              <th>Last error</th>
                              <th />
                            </tr>
                          </thead>
                          <tbody>
                            {data.webhooks.map((w) => (
                              <tr key={w.id}>
                                <td>
                                  <strong>{w.eventType || "Webhook event"}</strong>
                                  <small className="block muted">
                                    <Short id={w.eventId} /> · {date(w.createdAt)}
                                  </small>
                                </td>
                                <td>
                                  <Badge value={w.status} />
                                </td>
                                <td>{w.attempts} / 5</td>
                                <td>{w.responseCode || "—"}</td>
                                <td className="muted">{w.lastError || "—"}</td>
                                <td>
                                  {["FAILED", "SKIPPED"].includes(w.status) && (
                                    <button
                                      className="secondary compact"
                                      disabled={busy || isViewer}
                                      onClick={() =>
                                        act(async () => {
                                          await request(
                                            key,
                                            `/webhooks/${w.id}/retry`,
                                            { method: "POST" },
                                          );
                                          setNotice(
                                            "Webhook queued for redelivery",
                                          );
                                        })
                                      }
                                    >
                                      Retry
                                    </button>
                                  )}
                                </td>
                              </tr>
                            ))}
                          </tbody>
                        </table>
                      </div>
                    ) : (
                      <Empty text="Events arrive asynchronously after a payment or refund." />
                    )}
                  </section>
                </>
              )}
              {page === "Settings" && (
                <div className="settings-grid">
                  <section className="panel padded">
                    <h3>Business details</h3>
                    <form
                      key={merchant.id}
                      onSubmit={(e) => {
                        e.preventDefault();
                        const f = new FormData(e.currentTarget);
                        act(async () => {
                          await request(key, "/merchants/me", {
                            method: "PUT",
                            body: Object.fromEntries(f),
                          });
                          setNotice("Merchant settings saved");
                        });
                      }}
                    >
                      <label>
                        Business name
                        <input
                          name="name"
                          required
                          maxLength={100}
                          defaultValue={merchant.name}
                          disabled={isViewer}
                        />
                      </label>
                      <label>
                        Email address
                        <input
                          name="email"
                          type="email"
                          required
                          defaultValue={merchant.email}
                          disabled={isViewer}
                        />
                      </label>
                      <label>
                        Webhook URL
                        <input
                          name="webhookUrl"
                          type="url"
                          defaultValue={merchant.webhookUrl || ""}
                          placeholder="http://receiver:8081/webhooks/flowpay"
                          disabled={isViewer}
                        />
                      </label>
                      <p className="form-help">
                        With Docker Compose use http://receiver:8081/webhooks/flowpay.
                        If the backend runs directly on your computer, use
                        http://localhost:8081/webhooks/flowpay. The receiver must
                        have this merchant's signing secret saved in its inbox.
                        {" "}<a href="http://localhost:8081" target="_blank" rel="noopener noreferrer">Open notification inbox ↗</a>
                      </p>
                      <button className="primary" disabled={busy || isViewer}>
                        Save changes
                      </button>
                    </form>
                  </section>
                  <section className="panel padded">
                    <h3>Developer connection</h3>
                    <p className="muted">API key access · test environment</p>
                    <label>
                      Current merchant
                      <code className="code-block">{merchant.id}</code>
                    </label>
                    {apiKey && merchant.role !== "VIEWER" && <button
                      className="secondary"
                      onClick={() =>
                        act(async () => {
                          await navigator.clipboard.writeText(apiKey);
                          setNotice("API key copied");
                        })
                      }
                    >
                      <Copy size={16} />
                      Copy API key
                    </button>}
                    <p className="form-help">
                      Dashboard sign-in uses a separate expiring session. The integration API key is available here only after direct API-key connection or new merchant registration.
                    </p>
                    <hr />
                    <h3>Another business?</h3>
                    <p className="muted">
                      Each account has its own orders and balance.
                    </p>
                    <button
                      className="secondary"
                      onClick={() => open("register")}
                    >
                      <Plus size={16} />
                      Register merchant
                    </button>
                  </section>
                  {merchant.role === "OWNER" && (
                    <section className="panel padded">
                      <h3>Credential security</h3>
                      <p className="muted">Forgot a credential? Create a replacement after confirming your dashboard password.</p>
                      {authMode === "password" ? (
                        <div className="credential-actions">
                          <button className="secondary" onClick={() => open("rotateApiKey")}>
                            Make a new API key
                          </button>
                          <button className="secondary" onClick={() => open("rotateWebhookSecret")}>
                            Make a new webhook secret
                          </button>
                        </div>
                      ) : (
                        <p className="form-help">
                          Sign in with your email and password to manage credentials.
                          <button className="text-button" onClick={() => open("connect")}>Sign in</button>
                        </p>
                      )}
                    </section>
                  )}
                  <section className="panel padded">
                    <h3>Team access</h3>
                    <p className="muted">You are signed in as {merchant.role || "OWNER"}. Owners can manage accounts; viewers have read-only access.</p>
                    {merchant.role === "OWNER" && (
                      <>
                        <form onSubmit={(e) => {
                          e.preventDefault();
                          const form = new FormData(e.currentTarget);
                          const formElement = e.currentTarget;
                          act(async () => {
                            await request(key, "/users", {
                              method: "POST",
                              body: { email: form.get("teamEmail"), password: form.get("teamPassword"), role: form.get("teamRole") },
                            });
                            formElement.reset();
                            setNotice("Dashboard user added");
                          });
                        }}>
                          <label>Email address<input name="teamEmail" type="email" required maxLength={160} /></label>
                          <label>Temporary password<input name="teamPassword" type="password" required minLength={8} maxLength={72} /></label>
                          <label>Role<select name="teamRole" defaultValue="VIEWER"><option value="VIEWER">Viewer · read only</option><option value="OWNER">Owner · full access</option></select></label>
                          <button className="primary" disabled={busy}>Add dashboard user</button>
                        </form>
                        <div className="team-list">
                          {data.users.map((user) => (
                            <div className="team-user" key={user.id}><strong>{user.email}</strong><span className="badge">{user.role}</span></div>
                          ))}
                        </div>
                      </>
                    )}
                  </section>
                </div>
              )}
              {page === "Checkout" && checkout && (
                <div className="checkout-grid">
                  <section className="checkout-summary">
                    <span className="subtle-tag">FLOWPAY CHECKOUT</span>
                    <h2>{checkout.receipt}</h2>
                    <p>Payment to {merchant.name}</p>
                    <h1>{money(checkout.amount)}</h1>
                    <hr />
                    <p>
                      <Short id={checkout.id} />
                    </p>
                    <div className="checkout-trust">
                      <ShieldCheck size={21} />
                      <span>
                        Safe by design
                        <small>
                          No card numbers, PINs, or bank credentials.
                        </small>
                      </span>
                    </div>
                  </section>
                  <section className="panel padded">
                    {!result ? (
                      <form
                        onSubmit={(e) => {
                          e.preventDefault();
                          const f = new FormData(e.currentTarget);
                          act(async () => {
                            const p = await request(key, "/payments", {
                              method: "POST",
                              idempotencyKey: formKey,
                              body: {
                                orderId: checkout.id,
                                method: f.get("method"),
                                scenario: f.get("scenario"),
                              },
                            });
                            setResult(p);
                            setResult(
                              await request(key, `/payments/${p.id}/process`, {
                                method: "POST",
                              }),
                            );
                          });
                        }}
                      >
                        <h3>Choose how to pay</h3>
                        <div className="payment-methods">
                          {["UPI", "CARD", "WALLET", "NETBANKING"].map(
                            (m, i) => (
                              <label key={m}>
                                <input
                                  type="radio"
                                  name="method"
                                  value={m}
                                  defaultChecked={i === 0}
                                />
                                <CreditCard size={19} />
                                {m === "CARD"
                                  ? "Test card"
                                  : m === "NETBANKING"
                                    ? "Netbanking"
                                    : m}
                              </label>
                            ),
                          )}
                        </div>
                        <label>
                          Mock bank scenario
                          <select name="scenario">
                            <option value="SUCCESS">Successful payment</option>
                            <option value="FAILURE">
                              Bank declines payment
                            </option>
                            <option value="TIMEOUT">
                              Bank timeout — check after 20 seconds
                            </option>
                            <option value="DELAYED_SUCCESS">
                              Delayed success — about 8 seconds
                            </option>
                          </select>
                        </label>
                        <div className="info-banner small-info">
                          <FlaskConical size={18} />
                          This is a simulation. No money will be charged.
                        </div>
                        <button className="primary full" disabled={busy}>
                          {busy
                            ? "Processing…"
                            : `Pay ${money(checkout.amount)}`}
                          <ArrowRight size={17} />
                        </button>
                      </form>
                    ) : (
                      <div className="payment-result">
                        <span
                          className={
                            "result-icon " + result.status.toLowerCase()
                          }
                        >
                          {result.status === "SUCCESS" ? (
                            <Check size={32} />
                          ) : result.status === "FAILED" ? (
                            <X size={32} />
                          ) : (
                            <Clock size={32} />
                          )}
                        </span>
                        <h2>
                          {result.status === "SUCCESS"
                            ? "Payment successful"
                            : result.status === "FAILED"
                              ? "Payment declined"
                              : result.status === "CREATED"
                                ? "Ready to process"
                                : "Waiting for the bank"}
                        </h2>
                        <p>{money(result.amount)}</p>
                        <Badge value={result.status} />
                        <p className="form-help">
                          {result.status === "PENDING"
                            ? "Delayed payments update automatically. For a timeout, check the bank after 20 seconds."
                            : result.status === "FAILED"
                              ? "No money was collected. You can retry with a new simulation."
                              : "Your payment history is up to date."}
                        </p>
                        <Short id={result.id} />
                        {["PENDING", "CREATED"].includes(result.status) && (
                          <button
                            className="primary full"
                            disabled={busy}
                            onClick={() =>
                              act(async () =>
                                setResult(
                                  await request(
                                    key,
                                    `/payments/${result.id}/${result.status === "CREATED" ? "process" : "check"}`,
                                    { method: "POST" },
                                  ),
                                ),
                              )
                            }
                          >
                            <RefreshCw size={16} />
                            {result.status === "CREATED"
                              ? "Process payment"
                              : "Check bank status"}
                          </button>
                        )}
                        {result.status === "FAILED" && (
                          <button
                            className="primary full"
                            onClick={() => {
                              setResult(null);
                              setFormKey(newKey());
                            }}
                          >
                            Try again
                          </button>
                        )}
                        <button
                          className="secondary full"
                          onClick={() => changePage("Transactions")}
                        >
                          View transactions
                        </button>
                      </div>
                    )}
                  </section>
                </div>
              )}
            </>
          )}
          <footer className="page-footer">
            <span>Made for learning. Built to flow.</span>
            <span>
              <ShieldCheck size={13} /> Simulated payments only
            </span>
          </footer>
        </main>
      </div>
      {modal && (
        <Modal
          title={
            {
              order: "Create a payment order",
              refund: "Issue a refund",
              settle: "Settle your balance",
              detail: "Transaction details",
              connect: "Sign in to FlowPay",
              apiKey: "Connect with an API key",
              register: "Register your business",
              credentials: "Save your credentials",
              rotateApiKey: "Make a new API key",
              rotateWebhookSecret: "Make a new webhook secret",
              rotatedCredential: "Save your new credential",
            }[modal]
          }
      close={() => {
        if (!busy) {
          setModal(null);
          setCredentialResult(null);
        }
      }}
        >
          {error && (
            <p className="alert" role="alert">
              {error}
            </p>
          )}
          {modal === "order" && (
            <form
              onSubmit={(e) => {
                e.preventDefault();
                const f = new FormData(e.currentTarget);
                act(async () => {
                  const o = await request(key, "/orders", {
                    method: "POST",
                    body: {
                      receipt: f.get("receipt"),
                      amount: paise(f.get("amount")),
                    },
                  });
                  setModal(null);
                  beginCheckout(o);
                });
              }}
            >
              <label>
                Receipt / description
                <input
                  name="receipt"
                  placeholder="e.g. Website design sprint"
                  required
                  maxLength={100}
                />
              </label>
              <label>
                Amount (INR)
                <input
                  name="amount"
                  type="number"
                  min="1"
                  max="1000000"
                  step="0.01"
                  placeholder="2,499.00"
                  required
                />
              </label>
              <p className="form-help">
                Stored as integer paise. Minimum order: ₹1.
              </p>
              <button className="primary full" disabled={busy}>
                Create & open checkout <ArrowRight size={16} />
              </button>
            </form>
          )}
          {modal === "refund" && (
            <form
              onSubmit={(e) => {
                e.preventDefault();
                const f = new FormData(e.currentTarget);
                act(async () => {
                  await request(key, "/refunds", {
                    method: "POST",
                    idempotencyKey: formKey,
                    body: {
                      paymentId: f.get("paymentId"),
                      amount: paise(f.get("amount")),
                    },
                  });
                  setModal(null);
                  setNotice("Refund completed");
                  setPage("Refunds");
                });
              }}
            >
              <label>
                Successful payment
                <select
                  name="paymentId"
                  required
                  defaultValue={selected?.id || ""}
                >
                  <option value="" disabled>
                    Select payment
                  </option>
                  {successful
                    .filter((p) => p.refunded < p.amount)
                    .map((p) => (
                      <option key={p.id} value={p.id}>
                        {p.id.slice(0, 16)} · remaining{" "}
                        {money(p.amount - p.refunded)}
                      </option>
                    ))}
                </select>
              </label>
              <label>
                Refund amount (INR)
                <input
                  name="amount"
                  type="number"
                  min="0.01"
                  step="0.01"
                  required
                  defaultValue={
                    selected ? (selected.amount - selected.refunded) / 100 : ""
                  }
                />
              </label>
              <p className="form-help">
                Fees are returned proportionally. Refunds require enough
                unsettled balance.
              </p>
              <button className="primary full" disabled={busy}>
                Issue simulated refund
              </button>
            </form>
          )}
          {modal === "settle" && (
            <>
              <p>
                Transfer the available{" "}
                <strong>{money(merchant.balance)}</strong> to the mock merchant
                bank. Settled funds cannot fund subsequent refunds.
              </p>
              <button
                className="primary full"
                disabled={busy}
                onClick={() =>
                  act(async () => {
                    await request(key, "/settlements", {
                      method: "POST",
                      idempotencyKey: formKey,
                    });
                    setModal(null);
                    setNotice("Settlement completed");
                  })
                }
              >
                Confirm simulated settlement
              </button>
            </>
          )}
          {modal === "detail" && selected && (
            <>
              <dl className="details">
                {Object.entries({
                  Payment: selected.id,
                  Order: selected.orderId,
                  Status: selected.status,
                  Method: selected.method,
                  Scenario: selected.scenario,
                  Amount: money(selected.amount),
                  Fee: money(selected.fee),
                  Refunded: money(selected.refunded),
                  Created: date(selected.createdAt),
                }).map(([k, v]) => (
                  <React.Fragment key={k}>
                    <dt>{k}</dt>
                    <dd>{v}</dd>
                  </React.Fragment>
                ))}
              </dl>
              {selected.status === "SUCCESS" &&
                selected.refunded < selected.amount && (
                  <button
                    className="primary full"
                    onClick={() => open("refund", selected)}
                  >
                    Refund payment
                  </button>
                )}
              {["PENDING", "CREATED"].includes(selected.status) && (
                <button
                  className="primary full"
                  disabled={busy}
                  onClick={() =>
                    act(async () => {
                      setSelected(
                        await request(
                          key,
                          `/payments/${selected.id}/${selected.status === "CREATED" ? "process" : "check"}`,
                          { method: "POST" },
                        ),
                      );
                    })
                  }
                >
                  Check / process payment
                </button>
              )}
            </>
          )}
          {(modal === "rotateApiKey" || modal === "rotateWebhookSecret") && (
            <>
              <p>
                {modal === "rotateApiKey"
                  ? "Your current API key will stop working immediately. Update any integrations with the replacement."
                  : "Your current webhook secret will stop verifying signatures. Save the replacement in the receiver inbox before sending more events."}
              </p>
              <form onSubmit={(e) => {
                e.preventDefault();
                const password = new FormData(e.currentTarget).get("password");
                rotateCredential(modal === "rotateApiKey" ? "API key" : "Webhook secret", password);
              }}>
                <label>
                  Confirm your dashboard password
                  <input name="password" type="password" required autoComplete="current-password" />
                </label>
                <button className="primary full" disabled={busy}>
                  Confirm and make replacement
                </button>
              </form>
            </>
          )}
          {modal === "rotatedCredential" && credentialResult && (
            <>
              <p>
                Save this new {credentialResult.type.toLowerCase()} now. It will not be shown again.
                {credentialResult.type === "Webhook secret" && " Paste it into the receiver inbox at localhost:8081 so signatures verify."}
              </p>
              <label>
                New {credentialResult.type}
                <code className="code-block credential-value">{credentialResult.value}</code>
              </label>
              <button className="secondary full" onClick={() => act(async () => {
                await navigator.clipboard.writeText(credentialResult.value);
                setNotice(`${credentialResult.type} copied`);
              })}>
                <Copy size={16} /> Copy {credentialResult.type}
              </button>
              {credentialResult.type === "Webhook secret" && (
                <a className="text-button receiver-link" href="http://localhost:8081" target="_blank" rel="noopener noreferrer">
                  Open receiver inbox ↗
                </a>
              )}
            </>
          )}
          {modal === "connect" && (
            <form onSubmit={(e) => {
              e.preventDefault();
              const form = new FormData(e.currentTarget);
              act(async () => {
                const auth = await request("", "/auth/login", {
                  method: "POST",
                  body: { email: form.get("email"), password: form.get("password") },
                });
                setKey(auth.sessionToken);
                setApiKey("");
                setAuthMode("password");
                sessionStorage.setItem("flowpay-auth-mode", "password");
                sessionStorage.removeItem("flowpay-api-key");
                setModal(null);
              });
            }}>
              <label>Email address<input name="email" type="email" required autoComplete="username" /></label>
              <label>Password<input name="password" type="password" required autoComplete="current-password" /></label>
              <button className="primary full" disabled={busy}>Sign in</button>
              <button type="button" className="text-button" onClick={() => open("apiKey")}>Use an integration API key</button>
              <button type="button" className="text-button" onClick={() => open("register")}>Register a new merchant</button>
            </form>
          )}
          {modal === "apiKey" && (
            <form onSubmit={(e) => {
              e.preventDefault();
              const value = new FormData(e.currentTarget).get("key").trim();
              act(async () => {
                await request(value, "/merchants/me");
                setKey(value);
                setApiKey(value);
                setAuthMode("apiKey");
                sessionStorage.setItem("flowpay-auth-mode", "apiKey");
                sessionStorage.setItem("flowpay-api-key", value);
                setModal(null);
              });
            }}>
              <label>Merchant API key<input name="key" type="password" required placeholder="fp_test_…" /></label>
              <button className="primary full" disabled={busy}>Connect</button>
              <button type="button" className="text-button" onClick={() => open("connect")}>Use email and password</button>
            </form>
          )}
          {modal === "register" && (
            <form onSubmit={(e) => {
              e.preventDefault();
              const form = new FormData(e.currentTarget);
              act(async () => {
                const registered = await request("", "/auth/register", {
                  method: "POST",
                  body: Object.fromEntries(form),
                });
                setSelected(registered);
                setApiKey(registered.apiKey);
                setAuthMode("password");
                sessionStorage.setItem("flowpay-auth-mode", "password");
                sessionStorage.setItem("flowpay-api-key", registered.apiKey);
                setModal("credentials");
              });
            }}>
              <label>Business name<input name="name" required maxLength={100} /></label>
              <label>Email<input name="email" type="email" required maxLength={160} autoComplete="email" /></label>
              <label>Password<input name="password" type="password" required minLength={8} maxLength={72} autoComplete="new-password" /></label>
              <p className="form-help">Use this email and password to sign in. Your integration API key remains separate.</p>
              <button className="primary full" disabled={busy}>Create merchant account</button>
            </form>
          )}
          {modal === "credentials" && selected && (
            <>
              <p>
                Your account is ready. Save the integration API key and webhook signing secret; they are shown only once. Sign in to the dashboard with your email and password.
              </p>
              <label>
                API key<code className="code-block">{selected.apiKey}</code>
              </label>
              <label>
                Webhook signing secret
                <code className="code-block">{selected.webhookSecret}</code>
              </label>
              <button
                className="secondary full"
                onClick={() =>
                  act(async () => {
                    await navigator.clipboard.writeText(
                      JSON.stringify({ apiKey: selected.apiKey, webhookSecret: selected.webhookSecret }, null, 2),
                    );
                    setNotice("Credentials copied");
                  })
                }
              >
                <Copy size={16} />
                Copy credentials
              </button>
              <button
                className="primary full"
                onClick={() => {
                  setKey(selected.sessionToken);
                  setApiKey(selected.apiKey);
                  setAuthMode("password");
                  sessionStorage.setItem("flowpay-auth-mode", "password");
                  sessionStorage.setItem("flowpay-api-key", selected.apiKey);
                  setModal(null);
                  setPage("Overview");
                }}
              >
                Open my workspace <ArrowRight size={16} />
              </button>
            </>
          )}
        </Modal>
      )}
    </div>
  );
}
createRoot(document.getElementById("root")).render(<App />);
