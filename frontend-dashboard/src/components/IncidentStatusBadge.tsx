import type { IncidentStatus } from "../api/apiClient";
import StatusBadge, { type BadgeTone } from "./StatusBadge";

const TONE_BY_STATUS: Record<IncidentStatus, BadgeTone> = {
  OPEN: "red",
  INVESTIGATING: "orange",
  MITIGATED: "blue",
  RESOLVED: "green",
};

interface IncidentStatusBadgeProps {
  status: IncidentStatus;
}

export default function IncidentStatusBadge({ status }: IncidentStatusBadgeProps) {
  return <StatusBadge label={status} tone={TONE_BY_STATUS[status]} />;
}
