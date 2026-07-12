# Implementation Steps: Content Refresh Scheduler

## Step 1: `ContentRefreshScheduler`

- [x] `@Component` (gated on `meilisearch.enabled`) with a `@Scheduled(cron = "${open-elements.content.refresh-cron:0 0 * * * *}")` `refresh()`
- [x] Iterates enabled sources, calling `ContentIndexer.indexSource` and logging each `IndexReport`
- [x] Guards: skip when globally disabled; skip while `SearchReadinessState.isBootstrapping()`; non-overlap via `AtomicBoolean`
- [x] Per-source try/catch so one failing source does not stop the others

**Acceptance criteria:**
- [x] Project builds; behaviors verified by tests

**Related behaviors:** all

---

## Step 2: Tests

- [x] `ContentRefreshSchedulerTest` (real `ContentIndexer` + stub strategy + in-memory store + real `SearchReadinessState`): per-source, disabled skip, bootstrapping/global-disabled guards, non-overlap (re-entrancy), fault isolation, cron externalization (reflection), new/deleted propagation

**Acceptance criteria:**
- [x] All tests pass (`mvn test`); build green

---

## Behavior Coverage

| Scenario | Layer | Covered in Step |
|----------|-------|-----------------|
| Refresh runs each enabled source on tick | Backend | Step 2 (`runsEachEnabledSource`) |
| Disabled source is skipped | Backend | Step 2 (`disabledSourceIsSkipped`) |
| Cron is externally configurable | Backend | Step 2 (`cronIsExternallyConfigurable`) |
| Skips while bootstrapping | Backend | Step 2 (`skipsWhileBootstrapping`) |
| Skips when globally disabled | Backend | Step 2 (`skipsWhenGloballyDisabled`) |
| Non-overlapping runs | Backend | Step 2 (`nonOverlappingRuns`) |
| New page appears after refresh | Backend | Step 2 (`newPageIsAdded`) |
| Deleted page removed after refresh | Backend | Step 2 (`deletedPageIsRemoved`) |
| One source failing does not stop the others | Backend | Step 2 (`oneSourceFailingDoesNotStopOthers`) |

All scenarios are backend; there is no frontend in this spec.

## Notes

- `SearchReadinessState` reports `isBootstrapping()==true` until the runner finishes; the scheduler correctly skips during that window (tests mark bootstrap complete before asserting a run).
- Non-overlap is verified deterministically by re-entrantly calling `refresh()` during a run (no threads/sleeps).
