import { useEffect, useRef, useState } from "react";
import { Link, useParams } from "react-router-dom";
import {
  getIncidentById,
  getIncidentTimeline,
  getRunbooks,
  updateIncidentStatus,
  type Incident,
  type IncidentStatus,
  type Runbook,
  type TimelineEntry,
} from "../api/apiClient";
import IncidentStatusBadge from "../components/IncidentStatusBadge";
import TimelineEntryRow from "../components/TimelineEntryRow";
import RunbookPanel from "../components/RunbookPanel";
import "./IncidentDetailPage.css";

const NEXT_STATUS: Record<IncidentStatus, IncidentStatus | null> = {
  OPEN: "INVESTIGATING",
  INVESTIGATING: "MITIGATED",
  MITIGATED: "RESOLVED",
  RESOLVED: null,
};

export default function IncidentDetailPage() {
  const { id } = useParams<{ id: string }>();
  const [incident, setIncident] = useState<Incident | null>(null);
  const [timeline, setTimeline] = useState<TimelineEntry[]>([]);
  const [runbooks, setRunbooks] = useState<Runbook[]>([]);
  const [statusError, setStatusError] = useState<string | null>(null);
  const [isUpdatingStatus, setIsUpdatingStatus] = useState(false);
  const isUpdatingStatusRef = useRef(false);

  useEffect(() => {
    async function loadIncidentAndTimeline() {
      if (!id) return;
      const [incidentData, timelineData] = await Promise.all([
        getIncidentById(id),
        getIncidentTimeline(id),
      ]);
      setIncident(incidentData);
      setTimeline(timelineData);

      const runbookData = await getRunbooks(incidentData.serviceName);
      setRunbooks(runbookData);
    }

    loadIncidentAndTimeline();
  }, [id]);

  async function handleAdvanceStatus() {
    if (isUpdatingStatusRef.current) return;
    if (!id || !incident) return;
    const nextStatus = NEXT_STATUS[incident.status];
    if (!nextStatus) return;

    isUpdatingStatusRef.current = true;
    setIsUpdatingStatus(true);
    setStatusError(null);
    try {
      const result = await updateIncidentStatus(id, nextStatus);

      if (result.status === "updated") {
        const [incidentData, timelineData] = await Promise.all([
          getIncidentById(id),
          getIncidentTimeline(id),
        ]);
        setIncident(incidentData);
        setTimeline(timelineData);
      } else {
        setStatusError(result.message);
      }
    } finally {
      isUpdatingStatusRef.current = false;
      setIsUpdatingStatus(false);
    }
  }

  if (!incident) {
    return (
      <div className="incident-detail-page">
        <Link to="/" className="incident-detail-page__back">
          ← Back to dashboard
        </Link>
      </div>
    );
  }

  const nextStatus = NEXT_STATUS[incident.status];

  return (
    <div className="incident-detail-page">
      <Link to="/" className="incident-detail-page__back">
        ← Back to dashboard
      </Link>

      <section className="incident-detail-page__summary">
        <div className="incident-detail-page__summary-header">
          <h1>{incident.title}</h1>
          <IncidentStatusBadge status={incident.status} />
        </div>
        <p className="incident-detail-page__description">{incident.description}</p>
        <dl className="incident-detail-page__meta">
          <div>
            <dt>Service</dt>
            <dd>{incident.serviceName}</dd>
          </div>
          <div>
            <dt>Severity</dt>
            <dd>{incident.severity}</dd>
          </div>
          <div>
            <dt>Created</dt>
            <dd>{new Date(incident.createdAt).toLocaleString()}</dd>
          </div>
          <div>
            <dt>Updated</dt>
            <dd>{new Date(incident.updatedAt).toLocaleString()}</dd>
          </div>
        </dl>
      </section>

      <section className="incident-detail-page__status-control">
        {nextStatus ? (
          <button type="button" onClick={handleAdvanceStatus} disabled={isUpdatingStatus}>
            Mark as {nextStatus}
          </button>
        ) : (
          <p className="incident-detail-page__resolved-note">Incident resolved.</p>
        )}
        {statusError && <p className="incident-detail-page__status-error">{statusError}</p>}
      </section>

      <section className="incident-detail-page__timeline">
        <h2>Timeline</h2>
        <ul className="incident-detail-page__timeline-list">
          {timeline.map((entry) => (
            <TimelineEntryRow key={entry.id} entry={entry} />
          ))}
        </ul>
      </section>

      <section className="incident-detail-page__runbooks">
        <h2>Runbook</h2>
        <RunbookPanel runbooks={runbooks} />
      </section>
    </div>
  );
}
