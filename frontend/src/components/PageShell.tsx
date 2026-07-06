import { ReactNode } from "react";

type Props = { children: ReactNode; title?: string };

export const PageShell = ({ children, title }: Props) => (
  <div className="page-shell">
    {title && <h2 className="page-title">{title}</h2>}
    {children}
  </div>
);
