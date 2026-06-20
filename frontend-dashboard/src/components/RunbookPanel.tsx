import type { Runbook } from "../api/apiClient";
import "./RunbookPanel.css";

interface RunbookPanelProps {
  runbooks: Runbook[];
}

export default function RunbookPanel({ runbooks }: RunbookPanelProps) {
  if (runbooks.length === 0) {
    return <p className="runbook-panel__empty">No runbook available for this service.</p>;
  }

  return (
    <div className="runbook-panel">
      {runbooks.map((runbook) => (
        <div key={runbook.id} className="runbook-panel__item">
          <h3>{runbook.title}</h3>
          <pre className="runbook-panel__content">{runbook.content}</pre>
        </div>
      ))}
    </div>
  );
}
