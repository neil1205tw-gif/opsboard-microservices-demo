The patch handles future duplicate inserts but can break upgrades for databases already affected by the race it is fixing. Existing duplicate data must be cleaned before the unique constraint is applied.

Review comment:

- [P2] Deduplicate existing runbooks before adding uniqueness — C:\side_workspace\new\incident-service\src\main\java\com\opsboard\incident\entity\Runbook.java:18-18
  On a database that already contains duplicate `runbooks.service_name` rows from the pre-fix race, this new unique constraint makes Hibernate/MySQL reject the schema update before `RunbookSeeder` runs, so the new `save()` catch block cannot prevent startup or migration failure. Add a data cleanup/migration step that collapses existing duplicates before enforcing the constraint.