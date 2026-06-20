The gateway does not expose the existing runbook endpoint, leaving part of incident-service unreachable through the new unified API entry point.

Review comment:

- [P2] Route runbook requests through the gateway — C:\side_workspace\new\api-gateway\src\main\resources\application.yml:13-13
  When clients request runbooks through the gateway, this is the only incident-service predicate and it only matches `/api/incidents/**`, so the existing `GET /runbooks?serviceName=payment-api` API has no external gateway path such as `/api/runbooks?...` and falls through to a 404. Add a matching `/api/runbooks/**` route/predicate with the same prefix stripping so all incident-service APIs remain reachable through the gateway.