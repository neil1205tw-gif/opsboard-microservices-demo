const API_BASE_URL = import.meta.env.VITE_API_BASE_URL;

export type ServiceStatus = "HEALTHY" | "DEGRADED";

export interface ServiceHealth {
  serviceName: string;
  status: ServiceStatus;
  lastCheckedAt: string;
}

export interface ServiceMetrics {
  serviceName: string;
  latencyMs: number;
  errorRatePercent: number;
  cpuPercent: number;
  timestamp: string;
}

export type IncidentSeverity = "LOW" | "MEDIUM" | "HIGH" | "CRITICAL";
export type IncidentStatus = "OPEN" | "INVESTIGATING" | "MITIGATED" | "RESOLVED";

export interface Incident {
  id: number;
  title: string;
  description: string;
  serviceName: string;
  severity: IncidentSeverity;
  status: IncidentStatus;
  createdAt: string;
  updatedAt: string;
}

export type TriggerAlertResult =
  | { status: "created"; incident: Incident }
  | { status: "cooldown"; retryAfterSeconds: number };

export interface TimelineEntry {
  id: number;
  incidentId: number;
  fromStatus: IncidentStatus | null;
  toStatus: IncidentStatus;
  note: string;
  createdAt: string;
}

export interface Runbook {
  id: number;
  serviceName: string;
  title: string;
  content: string;
}

export type UpdateStatusResult =
  | { status: "updated"; incident: Incident }
  | { status: "conflict"; message: string };

async function request<T>(path: string, init?: RequestInit): Promise<T> {
  const response = await fetch(`${API_BASE_URL}${path}`, init);
  if (!response.ok) {
    throw new Error(`Request to ${path} failed with status ${response.status}`);
  }
  return response.json() as Promise<T>;
}

export function getServicesHealth(): Promise<ServiceHealth[]> {
  return request<ServiceHealth[]>("/api/ops/services/health");
}

export function getServiceMetrics(serviceName: string): Promise<ServiceMetrics> {
  return request<ServiceMetrics>(`/api/ops/services/${serviceName}/metrics`);
}

export async function triggerAlert(serviceName: string): Promise<TriggerAlertResult> {
  const response = await fetch(`${API_BASE_URL}/api/ops/services/${serviceName}/trigger-alert`, {
    method: "POST",
  });

  if (response.status === 201) {
    const incident = (await response.json()) as Incident;
    return { status: "created", incident };
  }

  if (response.status === 409) {
    const body = (await response.json()) as { retryAfterSeconds: number };
    return { status: "cooldown", retryAfterSeconds: body.retryAfterSeconds };
  }

  throw new Error(`Unexpected response triggering alert for ${serviceName}: ${response.status}`);
}

export function getIncidents(): Promise<Incident[]> {
  return request<Incident[]>("/api/incidents");
}

export function getIncidentById(id: string | number): Promise<Incident> {
  return request<Incident>(`/api/incidents/${id}`);
}

export function getIncidentTimeline(id: string | number): Promise<TimelineEntry[]> {
  return request<TimelineEntry[]>(`/api/incidents/${id}/timeline`);
}

export function getRunbooks(serviceName: string): Promise<Runbook[]> {
  return request<Runbook[]>(`/api/runbooks?serviceName=${encodeURIComponent(serviceName)}`);
}

export async function updateIncidentStatus(
  id: string | number,
  status: string
): Promise<UpdateStatusResult> {
  const response = await fetch(`${API_BASE_URL}/api/incidents/${id}/status`, {
    method: "PUT",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify({ status }),
  });

  if (response.ok) {
    const incident = (await response.json()) as Incident;
    return { status: "updated", incident };
  }

  if (response.status === 409) {
    const body = (await response.json()) as { message?: string };
    return { status: "conflict", message: body.message ?? "Invalid status transition" };
  }

  throw new Error(`Unexpected response updating status for incident ${id}: ${response.status}`);
}
