package com.opsboard.incident.config;

import com.opsboard.incident.entity.Runbook;
import com.opsboard.incident.repository.RunbookRepository;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

@Component
public class RunbookSeeder implements CommandLineRunner {

    private final RunbookRepository runbookRepository;

    public RunbookSeeder(RunbookRepository runbookRepository) {
        this.runbookRepository = runbookRepository;
    }

    @Override
    public void run(String... args) {
        seedIfAbsent(
                "payment-api",
                "Payment API Service Degradation Runbook",
                """
                1. Check the status dashboard for the upstream payment gateway and confirm whether the outage is on our side or the provider's side.
                2. Inspect payment-api logs for elevated error rates or timeout exceptions on outbound gateway calls.
                3. Check the transaction queue depth (payment.transactions queue); if backlog is growing, scale up queue consumers.
                4. Verify database connection pool usage on the payment-api instances; restart pool if exhausted.
                5. If errors persist after 10 minutes, restart the payment-api service instances in a rolling fashion.
                6. If the upstream gateway is confirmed down, enable the fallback/maintenance mode and notify the on-call payments lead.
                """
        );

        seedIfAbsent(
                "checkout-api",
                "Checkout API Service Degradation Runbook",
                """
                1. Check checkout-api health endpoint and recent deployment history for correlation with the incident start time.
                2. Inspect inventory-service and payment-api dependency latency, since checkout-api calls both synchronously.
                3. Check the cart session cache (Redis) hit rate; a sudden drop usually indicates cache eviction or connectivity issues.
                4. Review checkout-api error logs for 5xx spikes grouped by endpoint to isolate the failing flow.
                5. If a recent deploy is the suspected cause, roll back to the last known good version.
                6. Restart the checkout-api instances if CPU/memory usage indicates resource exhaustion.
                """
        );

        seedIfAbsent(
                "notification-worker",
                "Notification Worker Service Degradation Runbook",
                """
                1. Check the message queue backlog (notifications.outbound) to see if the worker is falling behind or stalled.
                2. Inspect notification-worker logs for repeated failures calling third-party providers (email/SMS/push).
                3. Verify provider API credentials/rate limits have not expired or been exceeded.
                4. Check worker pool/thread saturation; scale up worker replicas if the backlog keeps growing.
                5. Restart the notification-worker process if it is stuck (no log activity for several minutes).
                6. If a third-party provider outage is confirmed, switch to the backup provider if configured and notify stakeholders.
                """
        );
    }

    private void seedIfAbsent(String serviceName, String title, String content) {
        if (runbookRepository.existsByServiceName(serviceName)) {
            return;
        }
        Runbook runbook = new Runbook();
        runbook.setServiceName(serviceName);
        runbook.setTitle(title);
        runbook.setContent(content.strip());
        runbookRepository.save(runbook);
    }
}
