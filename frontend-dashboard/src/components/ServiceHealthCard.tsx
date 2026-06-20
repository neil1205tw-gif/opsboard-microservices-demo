import type { ServiceHealth, ServiceMetrics } from "../api/apiClient";
import StatusBadge from "./StatusBadge";
import TriggerAlertButton from "./TriggerAlertButton";
import "./ServiceHealthCard.css";

interface ServiceHealthCardProps {
  health: ServiceHealth;
  metrics: ServiceMetrics | null;
  onAlertCreated: () => void;
}

export default function ServiceHealthCard({ health, metrics, onAlertCreated }: ServiceHealthCardProps) {
  const isDegraded = health.status === "DEGRADED";

  return (
    <div className="service-health-card">
      <div className="service-health-card__header">
        <h3>{health.serviceName}</h3>
        <StatusBadge label={health.status} tone={isDegraded ? "red" : "green"} />
      </div>

      <dl className="service-health-card__metrics">
        <div>
          <dt>Latency</dt>
          <dd>{metrics ? `${metrics.latencyMs} ms` : "-"}</dd>
        </div>
        <div>
          <dt>Error Rate</dt>
          <dd>{metrics ? `${metrics.errorRatePercent}%` : "-"}</dd>
        </div>
        <div>
          <dt>CPU</dt>
          <dd>{metrics ? `${metrics.cpuPercent}%` : "-"}</dd>
        </div>
      </dl>

      {isDegraded && (
        <TriggerAlertButton serviceName={health.serviceName} onAlertCreated={onAlertCreated} />
      )}
    </div>
  );
}
