import { useEffect, useState } from "react";
import type { FormEvent } from "react";
import { Link } from "react-router-dom";
import {
  getCurrentEmployee,
  getMyAttendanceStatus,
  login,
  type EmployeeResponse,
} from "../api";
import { siteCheckIn, siteCheckOut } from "./Attendance";
import "./SiteAttendance.css";

export default function SiteAttendance() {
  const [username, setUsername] = useState("");
  const [password, setPassword] = useState("");
  const [employee, setEmployee] = useState<EmployeeResponse | null>(null);
  const [checkedIn, setCheckedIn] = useState(false);
  const [checkedOut, setCheckedOut] = useState(false);
  const [selfie, setSelfie] = useState<File | null>(null);
  const [preview, setPreview] = useState("");
  const [error, setError] = useState("");
  const [notice, setNotice] = useState("");
  const [loading, setLoading] = useState(false);
  const signedIn = Boolean(localStorage.getItem("nexushr_token"));

  useEffect(() => {
    if (!signedIn) {
      return;
    }
    let cancelled = false;
    void getCurrentEmployee()
      .then((me) => {
        if (!cancelled) {
          setEmployee(me);
        }
      })
      .catch(() => {
        if (!cancelled) {
          setError("Could not load your employee profile.");
        }
      });
    void getMyAttendanceStatus()
      .then((status) => {
        if (!cancelled) {
          setCheckedIn(Boolean(status.checkedIn));
          setCheckedOut(Boolean(status.checkedOut));
        }
      })
      .catch(() => undefined);
    return () => {
      cancelled = true;
    };
  }, [signedIn]);

  useEffect(() => {
    if (!selfie) {
      setPreview("");
      return;
    }
    const url = URL.createObjectURL(selfie);
    setPreview(url);
    return () => URL.revokeObjectURL(url);
  }, [selfie]);

  async function handleLogin(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    setError("");
    setNotice("");
    if (!username.trim() || !password) {
      setError("Enter your employee code or email and password.");
      return;
    }
    setLoading(true);
    try {
      const response = await login(username.trim(), password);
      localStorage.setItem("nexushr_token", response.accessToken);
      localStorage.setItem("nexushr_user", response.username);
      localStorage.setItem("nexushr_role", response.role);
      const me = await getCurrentEmployee();
      setEmployee(me);
    } catch (err) {
      const message = err instanceof Error ? err.message : "Login failed.";
      setError(
        message === "Failed to fetch"
          ? "The NexusHR server is not reachable. Start it, then try again."
          : message,
      );
    } finally {
      setLoading(false);
    }
  }

  function leave() {
    localStorage.removeItem("nexushr_token");
    localStorage.removeItem("nexushr_user");
    localStorage.removeItem("nexushr_role");
    setEmployee(null);
    setSelfie(null);
    setCheckedIn(false);
    setCheckedOut(false);
  }

  async function punch(kind: "in" | "out") {
    if (!selfie) {
      setError("Take a selfie photo first.");
      return;
    }
    setLoading(true);
    setError("");
    setNotice("");
    try {
      const saved = kind === "in" ? await siteCheckIn(selfie) : await siteCheckOut(selfie);
      setCheckedIn(Boolean(saved.checkInTime));
      setCheckedOut(Boolean(saved.checkOutTime));
      setSelfie(null);
      setNotice(kind === "in" ? "Checked in. The selfie is saved." : "Checked out. The selfie is saved.");
    } catch (err) {
      setError(err instanceof Error ? err.message : "Attendance could not be saved.");
    } finally {
      setLoading(false);
    }
  }

  const siteStaff = Boolean(employee?.siteVisitor);
  const sessionOpen = checkedIn && !checkedOut;

  return (
    <main className="site-app">
      <section className="site-card">
        <p>NexusHR site attendance</p>
        <h1>Check in with a selfie</h1>
        <p>
          This phone app is only for site staff. Check in and check out each need a photo.
          If you are still checked in at 11:00 PM, logout happens automatically and the attendance record stays.
        </p>

        {!signedIn && (
          <form onSubmit={(event) => void handleLogin(event)}>
            <label>
              Employee code or email
              <input
                type="text"
                autoComplete="username"
                value={username}
                onChange={(event) => setUsername(event.target.value)}
              />
            </label>
            <label>
              Password
              <input
                type="password"
                autoComplete="current-password"
                value={password}
                onChange={(event) => setPassword(event.target.value)}
              />
            </label>
            <button className="site-primary" type="submit" disabled={loading}>
              {loading ? "Signing in..." : "Login"}
            </button>
            <Link to="/login?create=1">Create your password with your email</Link>
          </form>
        )}

        {signedIn && employee && !siteStaff && (
          <div>
            <p>{employee.fullName}, this phone app is only for site staff.</p>
            <Link to="/">Open the office app</Link>
          </div>
        )}

        {signedIn && siteStaff && (
          <div className="site-actions">
            <p>
              {employee?.fullName}. {sessionOpen ? "You are checked in." : checkedOut ? "You are checked out." : "You are not checked in."}
            </p>
            <label className="site-photo">
              {selfie ? selfie.name : "Take a selfie photo"}
              <input
                type="file"
                accept="image/*"
                capture="environment"
                style={{ display: "none" }}
                onChange={(event) => setSelfie(event.target.files?.[0] ?? null)}
              />
            </label>
            {preview && <img className="site-preview" src={preview} alt="Selfie preview" />}
            <button
              className="site-primary"
              type="button"
              disabled={loading || sessionOpen || !selfie}
              onClick={() => void punch("in")}
            >
              Check in
            </button>
            <button
              className="site-secondary"
              type="button"
              disabled={loading || !sessionOpen || !selfie}
              onClick={() => void punch("out")}
            >
              Check out
            </button>
          </div>
        )}

        {error && <p className="site-error">{error}</p>}
        {notice && <p className="site-ok">{notice}</p>}

        {signedIn && (
          <button className="site-quiet" type="button" onClick={leave}>
            Sign out of this phone
          </button>
        )}
      </section>
    </main>
  );
}
