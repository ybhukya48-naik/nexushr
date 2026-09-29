import { useCallback, useEffect, useMemo, useState } from "react";
import "./Notifications.css";
import {
  getCurrentEmployee,
  getEmployeeNotifications,
  getEmployees,
  markNotificationAsRead,
  sendNotification,
  type EmployeeResponse,
  type NotificationResponse,
} from "../api";

function formatDate(value?: string) {
  if (!value) return "Recently";

  const date = new Date(value);

  if (Number.isNaN(date.getTime())) {
    return value;
  }

  return new Intl.DateTimeFormat("en-IN", {
    dateStyle: "medium",
    timeStyle: "short",
  }).format(date);
}

function typeLabel(type: NotificationResponse["notificationType"]) {
  switch (type) {
    case "APPROVAL":
      return "Approval";
    case "REMINDER":
      return "Reminder";
    case "ANNOUNCEMENT":
      return "Announcement";
    default:
      return "General";
  }
}

function typeIcon(type: NotificationResponse["notificationType"]) {
  switch (type) {
    case "APPROVAL":
      return "OK";
    case "REMINDER":
      return "!";
    case "ANNOUNCEMENT":
      return "ANN";
    default:
      return "N";
  }
}

function sortNotifications(data: NotificationResponse[]) {
  return [...data].sort(
    (a, b) =>
      new Date(b.createdAt || 0).getTime() -
      new Date(a.createdAt || 0).getTime(),
  );
}

export default function Notifications() {
  const [employee, setEmployee] = useState<EmployeeResponse | null>(null);
  const [notifications, setNotifications] = useState<NotificationResponse[]>(
    [],
  );
  const [scheduledNotification, setScheduledNotification] = useState<{
    employeeId: number;
    employeeName: string;
    title: string;
    message: string;
    notificationType: NotificationResponse["notificationType"];
    channel: string;
    scheduledAt: string;
  } | null>(null);
  const [loading, setLoading] = useState(true);
  const [refreshing, setRefreshing] = useState(false);
  const [error, setError] = useState("");
  const [filter, setFilter] = useState<"ALL" | "UNREAD">("ALL");
  const [employees, setEmployees] = useState<EmployeeResponse[]>([]);
  const [showCreate, setShowCreate] = useState(false);
  const [sending, setSending] = useState(false);
  const [success, setSuccess] = useState("");
  const [selectedEmployeeId, setSelectedEmployeeId] = useState("");
  const [notificationType, setNotificationType] = useState<NotificationResponse["notificationType"]>("GENERAL");
  const [channel, setChannel] = useState("IN_APP");
  const [title, setTitle] = useState("");
  const [message, setMessage] = useState("");
  const [scheduledAt, setScheduledAt] = useState("");

  const role = localStorage.getItem("nexushr_role") || "HR";
  const canCreate = ["ADMIN", "HR", "MANAGER"].includes(role);

  const activeEmployees = useMemo(
    () => employees.filter((item) => item.active !== false),
    [employees],
  );

  const loadEmployeeNotifications = useCallback(
    async (employeeId: number) => {
      const data = await getEmployeeNotifications(employeeId);
      setNotifications(sortNotifications(data));
    },
    [],
  );

  const loadNotifications = useCallback(
    async (initial = true) => {
      try {
        if (initial) {
          setLoading(true);
        } else {
          setRefreshing(true);
        }

        setError("");

        if (canCreate) {
          const employeeData = await getEmployees();
          setEmployees(employeeData);

          if (selectedEmployeeId) {
            const selected = employeeData.find(
              (item) => item.id === Number(selectedEmployeeId),
            );

            if (selected) {
              setEmployee(selected);
              await loadEmployeeNotifications(selected.id);
              return;
            }
          }

          setEmployee(null);
          setNotifications([]);
          return;
        }

        const currentEmployee = await getCurrentEmployee();

        setEmployee(currentEmployee);
        setSelectedEmployeeId(String(currentEmployee.id));
        await loadEmployeeNotifications(currentEmployee.id);
      } catch (err) {
        console.error(err);
        setError(
          err instanceof Error
            ? err.message
            : "Unable to load notifications.",
        );
      } finally {
        setLoading(false);
        setRefreshing(false);
      }
    },
    [canCreate, loadEmployeeNotifications, selectedEmployeeId],
  );
  useEffect(() => {
    void loadNotifications();
  }, [loadNotifications]);

  const unreadCount = useMemo(
    () => notifications.filter((item) => !item.read).length,
    [notifications],
  );

  const visibleNotifications = useMemo(
    () =>
      filter === "UNREAD"
        ? notifications.filter((item) => !item.read)
        : notifications,
    [filter, notifications],
  );

  const isScheduled = Boolean(scheduledAt);

  function formatScheduledDate(value?: string) {
    if (!value) return "";

    const date = new Date(value);

    if (Number.isNaN(date.getTime())) {
      return value;
    }

    return new Intl.DateTimeFormat("en-IN", {
      dateStyle: "medium",
      timeStyle: "short",
    }).format(date);
  }

  async function handleEmployeeChange(value: string) {
    setSelectedEmployeeId(value);
    setError("");
    setSuccess("");

    if (!value) {
      setNotifications([]);
      return;
    }

    const selected = employees.find((item) => item.id === Number(value));

    if (!selected) {
      setNotifications([]);
      return;
    }

    try {
      setRefreshing(true);
      setEmployee(selected);

      const data = await getEmployeeNotifications(selected.id);
      setNotifications(sortNotifications(data));
    } catch (err) {
      console.error(err);
      setError(
        err instanceof Error
          ? err.message
          : "Unable to load employee notifications.",
      );
      setNotifications([]);
    } finally {
      setRefreshing(false);
    }
  }

  async function handleCreateNotification(
    event: React.FormEvent<HTMLFormElement>,
  ) {
    event.preventDefault();

    const selected = employees.find(
      (item) => item.id === Number(selectedEmployeeId),
    );

    if (!selected) {
      setError("Please select an employee.");
      return;
    }

    if (selected.active === false) {
      setError("Notifications can only be sent to active employees.");
      return;
    }

    if (!title.trim() || !message.trim()) {
      setError("Title and message are required.");
      return;
    }

    try {
      setSending(true);
      setError("");
      setSuccess("");

      await sendNotification({
        employeeId: selected.id,
        title: title.trim(),
        message: message.trim(),
        notificationType,
        channel,
        recipientEmail:
          channel === "EMAIL" || channel === "BOTH"
            ? selected.email
            : undefined,
        recipientPhone:
          channel === "SMS" || channel === "BOTH"
            ? selected.phone
            : undefined,
        scheduledAt: scheduledAt || undefined,
      });

      // Immediately reload the selected employee's notifications so
      // the new notification appears without a manual page refresh.
      setEmployee(selected);
      await loadEmployeeNotifications(selected.id);

      setSuccess(
        isScheduled
          ? `Notification scheduled successfully for ${selected.fullName}.`
          : `Notification sent successfully to ${selected.fullName}.`,
      );

      setShowCreate(false);
      setTitle("");
      setMessage("");
      setScheduledAt("");
      setNotificationType("GENERAL");
      setChannel("IN_APP");

      setSelectedEmployeeId(String(selected.id));

      if (isScheduled) {
        setScheduledNotification({
          employeeId: selected.id,
          employeeName: selected.fullName,
          title: title.trim(),
          message: message.trim(),
          notificationType,
          channel,
          scheduledAt,
        });
      } else {
        setScheduledNotification(null);
        const data = await getEmployeeNotifications(selected.id);
        setNotifications(sortNotifications(data));
      }
    } catch (err) {
      console.error(err);
      setError(
        err instanceof Error
          ? err.message
          : "Unable to send notification.",
      );
    } finally {
      setSending(false);
    }
  }

  async function handleMarkRead(id: number) {
    try {
      const updated = await markNotificationAsRead(id);

      setNotifications((current) =>
        sortNotifications(
          current.map((item) => (item.id === id ? updated : item)),
        ),
      );
    } catch (err) {
      console.error(err);
      setError(
        err instanceof Error
          ? err.message
          : "Unable to mark notification as read.",
      );
    }
  }

  if (loading) {
    return (
      <div className="page-heading">
        <div>
          <p className="eyebrow">WORKFORCE COMMUNICATION</p>
          <h2>Notifications</h2>
          <p>Loading notifications...</p>
        </div>
      </div>
    );
  }

  return (
    <>
      <div className="page-heading">
        <div>
          <p className="eyebrow">WORKFORCE COMMUNICATION</p>
          <h2>Notifications</h2>
          <p>
            {employee
              ? `Updates and alerts for ${employee.fullName}.`
              : "Your approvals, reminders, announcements and alerts."}
          </p>
        </div>

        <button
          className="primary-button"
          onClick={() => void loadNotifications(false)}
          disabled={refreshing}
        >
          {refreshing ? "Refreshing..." : "Refresh"}
        </button>
      </div>

      {error && (
        <section className="panel">
          <div style={{ display: "flex", gap: 12, alignItems: "center" }}>
            <div className="stat-icon">!</div>
            <div>
              <strong>Notification action unavailable</strong>
              <p>{error}</p>
            </div>
          </div>
        </section>
      )}

      {canCreate && (
        <section className="panel">
          <div className="panel-header">
            <div>
              <h3>Notification Management</h3>
              <p>
                Select an active employee to view their notifications or send
                a new alert.
              </p>
            </div>

            <button
              className="primary-button"
              type="button"
              onClick={() => {
                setShowCreate((current) => !current);
                setError("");
                setSuccess("");
              }}
            >
              {showCreate ? "Close" : "+ Create Notification"}
            </button>
          </div>

          <div
            style={{
              display: "grid",
              gridTemplateColumns: "minmax(240px, 1fr)",
              gap: 14,
              marginBottom: showCreate ? 18 : 0,
            }}
          >
            <label>
              Employee
              <select
                value={selectedEmployeeId}
                onChange={(event) =>
                  void handleEmployeeChange(event.target.value)
                }
              >
                <option value="">Select active employee</option>
                {activeEmployees.map((item) => (
                  <option key={item.id} value={item.id}>
                    {item.fullName} - {item.employeeCode}
                  </option>
                ))}
              </select>
            </label>
          </div>

          {showCreate && (
            <form
              onSubmit={handleCreateNotification}
              style={{
                display: "grid",
                gap: 14,
                gridTemplateColumns:
                  "repeat(auto-fit, minmax(220px, 1fr))",
              }}
            >
              <label>
                Notification Type
                <select
                  value={notificationType}
                  onChange={(event) =>
                    setNotificationType(event.target.value as NotificationResponse["notificationType"])
                  }
                >
                  <option value="GENERAL">General</option>
                  <option value="APPROVAL">Approval</option>
                  <option value="REMINDER">Reminder</option>
                  <option value="ANNOUNCEMENT">Announcement</option>
                </select>
              </label>

              <label>
                Delivery Channel
                <select
                  value={channel}
                  onChange={(event) => setChannel(event.target.value)}
                >
                  <option value="IN_APP">In-App</option>
                  <option value="EMAIL">Email</option>
                  <option value="SMS">SMS</option>
                  <option value="BOTH">Email + SMS</option>
                </select>
              </label>

              <label>
                Notification Date & Time
                <input
                  type="datetime-local"
                  value={scheduledAt}
                  onChange={(event) => setScheduledAt(event.target.value)}
                />
                <small>Leave empty to send immediately.</small>
              </label>

              <label>
                Title
                <input
                  value={title}
                  onChange={(event) => setTitle(event.target.value)}
                  placeholder="Notification title"
                  required
                />
              </label>

              <label style={{ gridColumn: "1 / -1" }}>
                Message
                <textarea
                  value={message}
                  onChange={(event) => setMessage(event.target.value)}
                  placeholder="Write notification message..."
                  rows={4}
                  required
                />
              </label>

              <div style={{ gridColumn: "1 / -1" }}>
                <button
                  className="primary-button"
                  type="submit"
                  disabled={sending || !selectedEmployeeId}
                >
                  {sending
                    ? isScheduled
                      ? "Scheduling..."
                      : "Sending..."
                    : isScheduled
                      ? "Schedule Notification"
                      : "Send Notification"}
                </button>
              </div>
            </form>
          )}
        </section>
      )}

      {success && (
        <section className="panel">
          <strong>OK - {success}</strong>
        </section>
      )}

      <div className="stats-grid">
        <div className="stat-card">
          <div className="stat-icon">N</div>
          <p>Total Notifications</p>
          <h3>{notifications.length}</h3>
          <span>Live from Cyond</span>
        </div>

        <div className="stat-card">
          <div className="stat-icon">!</div>
          <p>Unread</p>
          <h3>{unreadCount}</h3>
          <span>Requires attention</span>
        </div>

        <div className="stat-card">
          <div className="stat-icon">OK</div>
          <p>Read</p>
          <h3>{notifications.length - unreadCount}</h3>
          <span>Already reviewed</span>
        </div>

        <div className="stat-card">
          <div className="stat-icon">U</div>
          <p>Employee</p>
          <h3>{employee?.employeeCode || "-"}</h3>
          <span>{employee?.designation || "Employee profile"}</span>
        </div>
      </div>

      <section className="panel">
        <div className="panel-header">
          <div>
            <h3>
              {canCreate && employee
                ? `${employee.fullName}'s Notifications`
                : "Your Notifications"}
            </h3>

            <p>
              {unreadCount > 0
                ? `${unreadCount} unread notification${
                    unreadCount === 1 ? "" : "s"
                  }`
                : "You're all caught up."}
            </p>
          </div>

          <div style={{ display: "flex", gap: 8 }}>
            <button
              className="primary-button"
              onClick={() => setFilter("ALL")}
              style={{ opacity: filter === "ALL" ? 1 : 0.65 }}
            >
              All
            </button>

            <button
              className="primary-button"
              onClick={() => setFilter("UNREAD")}
              style={{ opacity: filter === "UNREAD" ? 1 : 0.65 }}
            >
              Unread {unreadCount > 0 ? `(${unreadCount})` : ""}
            </button>
          </div>
        </div>

        {scheduledNotification &&
          selectedEmployeeId === String(scheduledNotification.employeeId) && (
            <article
              style={{
                display: "grid",
                gridTemplateColumns: "46px minmax(0, 1fr) auto",
                gap: 16,
                alignItems: "start",
                padding: 18,
                marginBottom: 12,
                border: "1px solid #d6bbfb",
                borderRadius: 16,
                background: "linear-gradient(135deg, #faf7ff 0%, #f5f3ff 100%)",
              }}
            >
              <div
                style={{
                  width: 46,
                  height: 46,
                  borderRadius: 14,
                  display: "grid",
                  placeItems: "center",
                  background: "#ede9fe",
                  color: "#6941c6",
                  fontWeight: 800,
                  fontSize: 18,
                }}
              >
                S
              </div>

              <div style={{ minWidth: 0 }}>
                <div
                  style={{
                    display: "flex",
                    alignItems: "center",
                    gap: 10,
                    flexWrap: "wrap",
                    marginBottom: 6,
                  }}
                >
                  <strong>{scheduledNotification.title}</strong>

                  <span
                    style={{
                      display: "inline-flex",
                      alignItems: "center",
                      padding: "4px 9px",
                      borderRadius: 999,
                      background: "#ede9fe",
                      color: "#6941c6",
                      fontSize: 12,
                      fontWeight: 700,
                    }}
                  >
                    SCHEDULED
                  </span>

                  <span className="badge">
                    {typeLabel(scheduledNotification.notificationType)}
                  </span>
                </div>

                <p>{scheduledNotification.message}</p>

                <small>
                  Scheduled for{" "}
                  {new Date(
                    scheduledNotification.scheduledAt,
                  ).toLocaleString(undefined, {
                    dateStyle: "medium",
                    timeStyle: "short",
                  })}
                  {" - "}
                  {scheduledNotification.channel}
                </small>
              </div>
            </article>
          )}
        {visibleNotifications.length === 0 ? (
          <div className="empty-module">
            <div className="module-icon">N</div>
            <h3>
              {canCreate && !selectedEmployeeId
                ? "Select an employee"
                : filter === "UNREAD"
                  ? "No unread notifications"
                  : "No notifications yet"}
            </h3>

            <p>
              {canCreate && !selectedEmployeeId
                ? "Select an active employee above to view their notifications."
                : filter === "UNREAD"
                  ? "All notifications for this employee have been reviewed."
                  : "New approvals, reminders and announcements will appear here."}
            </p>
          </div>
        ) : (
          <div style={{ display: "grid", gap: 12 }}>
            {visibleNotifications.map((notification) => (
              <article
                key={notification.id}
                style={{
                  display: "grid",
                  gridTemplateColumns: "46px minmax(0, 1fr) auto",
                  gap: 16,
                  alignItems: "start",
                  padding: 18,
                  border: "1px solid #e2e8f0",
                  borderRadius: 16,
                  background: notification.read ? "#fff" : "#f8fafc",
                }}
              >
                <div className="stat-icon">
                  {typeIcon(notification.notificationType)}
                </div>

                <div>
                  <div
                    style={{
                      display: "flex",
                      gap: 8,
                      alignItems: "center",
                      flexWrap: "wrap",
                    }}
                  >
                    <strong>{notification.title}</strong>

                    {!notification.read && (
                      <span className="badge high">NEW</span>
                    )}

                    <span className="badge">
                      {typeLabel(notification.notificationType)}
                    </span>
                  </div>

                  <p>{notification.message}</p>

                  <small>
                    {notification.scheduledAt
                      ? `Scheduled: ${formatScheduledDate(notification.scheduledAt)} - `
                      : ""}
                    {formatDate(notification.createdAt)} -{" "}
                    {notification.channel} -{" "}
                    {notification.deliveryStatus}
                  </small>
                </div>

                {!notification.read && (
                  <button
                    className="primary-button"
                    onClick={() => void handleMarkRead(notification.id)}
                  >
                    Mark read
                  </button>
                )}
              </article>
            ))}
          </div>
        )}
      </section>
    </>
  );
}


