import { NavLink, useNavigate } from "react-router-dom";
import { clearAuth, getRole, getUser } from "../api/client";

const links = [
  { to: "/dashboard",   label: "Dashboard" },
  { to: "/employees",   label: "Employees" },
  { to: "/leave",       label: "Leave" },
  { to: "/attendance",  label: "Attendance" },
  { to: "/payroll",     label: "Payroll" },
  { to: "/performance", label: "Performance" },
  { to: "/ai",          label: "AI Insights" },
];

export const Nav = () => {
  const navigate = useNavigate();
  const role = getRole();
  const user = getUser();

  const handleLogout = () => {
    clearAuth();
    navigate("/login");
  };

  const visibleLinks = links.filter((l) => {
    if (role === "EMPLOYEE") return ["/dashboard", "/leave", "/attendance"].includes(l.to);
    return true;
  });

  return (
    <nav className="nav">
      <span className="nav-brand">NexusHR</span>
      <div className="nav-links">
        {visibleLinks.map((l) => (
          <NavLink key={l.to} to={l.to} className={({ isActive }) => isActive ? "nav-link active" : "nav-link"}>
            {l.label}
          </NavLink>
        ))}
      </div>
      <div className="nav-user">
        <span className="nav-badge">{role}</span>
        <span className="nav-username">{user}</span>
        <button className="nav-logout" onClick={handleLogout}>Sign out</button>
      </div>
    </nav>
  );
};
