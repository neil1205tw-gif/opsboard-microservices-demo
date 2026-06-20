import { useState } from "react";
import { triggerAlert } from "../api/apiClient";

interface TriggerAlertButtonProps {
  serviceName: string;
  onAlertCreated: () => void;
}

export default function TriggerAlertButton({ serviceName, onAlertCreated }: TriggerAlertButtonProps) {
  const [message, setMessage] = useState<string | null>(null);

  async function handleClick() {
    const result = await triggerAlert(serviceName);

    if (result.status === "created") {
      setMessage(`Alert triggered, incident #${result.incident.id} created`);
      onAlertCreated();
    } else {
      setMessage(`Cooldown active, retry in ${result.retryAfterSeconds}s`);
    }
  }

  return (
    <div className="trigger-alert">
      <button type="button" onClick={handleClick}>
        Trigger Alert
      </button>
      {message && <p className="trigger-alert__message">{message}</p>}
    </div>
  );
}
