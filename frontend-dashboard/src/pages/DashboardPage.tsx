import { useEffect, useState } from "react";
import {
  getIncidents,
  getServiceMetrics,
  getServicesHealth,
  type Incident,
  type ServiceHealth,
  type ServiceMetrics,
} from "../api/apiClient";
import ServiceHealthCard from "../components/ServiceHealthCard";
import IncidentListItem from "../components/IncidentListItem";
import "./DashboardPage.css";

export default function DashboardPage() {
  const [servicesHealth, setServicesHealth] = useState<ServiceHealth[]>([]);
  const [metricsByService, setMetricsByService] = useState<Record<string, ServiceMetrics>>({});
  const [incidents, setIncidents] = useState<Incident[]>([]);

  useEffect(() => {
    loadServicesHealth();
    loadIncidents();
  }, []);

  async function loadServicesHealth() {
    const health = await getServicesHealth();
    setServicesHealth(health);

    const metricsEntries = await Promise.all(
      health.map(async (service) => {
        const metrics = await getServiceMetrics(service.serviceName);
        return [service.serviceName, metrics] as const;
      })
    );
    setMetricsByService(Object.fromEntries(metricsEntries));
  }

  async function loadIncidents() {
    const data = await getIncidents();
    setIncidents(data);
  }

  return (
    <div className="dashboard-page">
      <h1>OpsBoard Dashboard</h1>

      <section className="dashboard-page__services">
        {servicesHealth.map((health) => (
          <ServiceHealthCard
            key={health.serviceName}
            health={health}
            metrics={metricsByService[health.serviceName] ?? null}
            onAlertCreated={loadIncidents}
          />
        ))}
      </section>

      <section className="dashboard-page__incidents">
        <h2>Incidents</h2>
        {incidents.length === 0 ? (
          <p className="dashboard-page__empty">No incidents.</p>
        ) : (
          <ul className="dashboard-page__incident-list">
            {incidents.map((incident) => (
              <IncidentListItem key={incident.id} incident={incident} />
            ))}
          </ul>
        )}
      </section>
    </div>
  );
}
