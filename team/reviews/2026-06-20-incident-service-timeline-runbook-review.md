The feature works for a single startup path, but the seed logic is not safe under concurrent service starts and can violate the documented idempotency invariant.

Review comment:

- [P2] Make runbook seeding atomic — C:\side_workspace\new\incident-service\src\main\java\com\opsboard\incident\config\RunbookSeeder.java:60-67
  When two incident-service instances start against an empty database at the same time, both can observe `existsByServiceName(serviceName)` as false and then both insert the same seed row; because `runbooks.service_name` has no unique constraint, `GET /runbooks?serviceName=payment-api` can return two entries, violating the required one-runbook-per-service invariant. Add a database uniqueness constraint and handle duplicate insert/upsert semantics so startup remains idempotent under concurrent deployments.