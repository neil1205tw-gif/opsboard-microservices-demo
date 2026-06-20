import { Link } from "react-router-dom";
import type { Incident } from "../api/apiClient";
import IncidentStatusBadge from "./IncidentStatusBadge";
import "./IncidentListItem.css";

interface IncidentListItemProps {
  incident: Incident;
}

export default function IncidentListItem({ incident }: IncidentListItemProps) {
  return (
    <li className="incident-list-item">
      <Link to={`/incidents/${incident.id}`} className="incident-list-item__link">
        <span className="incident-list-item__title">{incident.title}</span>
        <span className="incident-list-item__service">{incident.serviceName}</span>
        <span className="incident-list-item__severity">{incident.severity}</span>
        <IncidentStatusBadge status={incident.status} />
        <span className="incident-list-item__date">
          {new Date(incident.createdAt).toLocaleString()}
        </span>
      </Link>
    </li>
  );
}
