import "./StatusBadge.css";

export type BadgeTone = "green" | "red" | "orange" | "blue" | "gray";

interface StatusBadgeProps {
  label: string;
  tone: BadgeTone;
}

export default function StatusBadge({ label, tone }: StatusBadgeProps) {
  return <span className={`status-badge status-badge--${tone}`}>{label}</span>;
}
