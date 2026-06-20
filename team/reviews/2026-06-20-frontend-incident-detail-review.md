The incident detail flow mostly works, but the state-changing status button can submit duplicate stale transitions while a request is already in flight, causing incorrect timeline/error behavior. This should be fixed before the patch is considered correct.

Review comment:

- [P2] Disable status advance while request is pending — C:\side_workspace\new\frontend-dashboard\src\pages\IncidentDetailPage.tsx:115-115
  When the user double-clicks or clicks again while the PUT/refetch is still pending, this button sends multiple updates using the same stale `nextStatus`. The first request can succeed, while a later one either creates a duplicate timeline transition under concurrent backend reads or returns 409 and displays an error even though the status advanced. Track a pending state and disable/ignore clicks until the update and refresh finish.