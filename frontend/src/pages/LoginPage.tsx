import { FormEvent, useState } from "react";
import { useNavigate } from "react-router-dom";
import { login } from "../api/client";

export const LoginPage = () => {
  const [username, setUsername] = useState("admin");
  const [password, setPassword] = useState("password");
  const [error, setError] = useState("");
  const [loading, setLoading] = useState(false);
  const navigate = useNavigate();

  const handleSubmit = async (e: FormEvent) => {
    e.preventDefault();
    setError("");
    setLoading(true);
    try {
      await login(username, password);
      navigate("/dashboard");
    } catch (err) {
      const message = err instanceof Error ? err.message : "";
      setError(message || "Authentication failed. Try admin / hr / manager with any password.");
    } finally {
      setLoading(false);
    }
  };

  return (
    <div className="login-wrapper">
      <div className="login-card">
        <h1 className="login-brand">NexusHR</h1>
        <p className="login-sub">AI-Enabled Enterprise Workforce Intelligence</p>
        <form onSubmit={handleSubmit} className="form">
          <label>
            Username
            <input value={username} onChange={(e) => setUsername(e.target.value)} autoFocus />
          </label>
          <label>
            Password
            <input type="password" value={password} onChange={(e) => setPassword(e.target.value)} />
          </label>
          {error && <p className="form-error">{error}</p>}
          <button type="submit" disabled={loading} className="btn-primary">
            {loading ? "Signing in…" : "Sign in"}
          </button>
        </form>
        <p className="login-hint">Demo users: <code>admin</code>, <code>hr</code>, <code>manager</code>, <code>employee</code></p>
      </div>
    </div>
  );
};
