import type { TimelineEntry } from "../api/apiClient";
import IncidentStatusBadge from "./IncidentStatusBadge";
import "./TimelineEntryRow.css";

interface TimelineEntryRowProps {
  entry: TimelineEntry;
}

export default function TimelineEntryRow({ entry }: TimelineEntryRowProps) {
  return (
    <li className="timeline-entry-row">
      <span className="timeline-entry-row__from">
        {entry.fromStatus ?? "(created)"}
      </span>
      <span className="timeline-entry-row__arrow">→</span>
      <IncidentStatusBadge status={entry.toStatus} />
      <span className="timeline-entry-row__date">
        {new Date(entry.createdAt).toLocaleString()}
      </span>
    </li>
  );
}
