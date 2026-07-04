# Design: `allowVariantMultiThreadAccess` in the network-store IIDM implementation

Status: **draft / prototyping** — see [Prototype status](#prototype-status) at the end.

## 1. Context

`VariantManager.allowVariantMultiThreadAccess(boolean)` is part of the IIDM API. In this
implementation it is currently a hard stub:

```java
// VariantManagerImpl
public void allowVariantMultiThreadAccess(boolean allow) {
    throw new PowsyblException("Network store implementation does not support multi-thread access yet");
}
```

The target contract, aligned with powsybl-core:

- concurrent **read** access to **distinct** variants of the same `Network` from multiple
  threads, each thread selecting its own working variant with `setWorkingVariant`;
- a thread that has not selected a variant fails with `Variant index not set`;
- out of scope (same as core): concurrent structural modification, concurrent
  `cloneVariant`/`removeVariant`, concurrent writes to the same variant.

## 2. Why core's design does not transfer

In powsybl-core, the state of **all** variants is already in memory: every equipment stores
its per-variant state in arrays indexed by variant. Switching variant means changing an
index; multi-thread support means putting that index in a `ThreadLocal`. Both operations
are essentially free.

Here, resources are **persisted** (database behind network-store-server) and fetched over
HTTP. Loading a variant's resources costs real IO and time, and the whole client
architecture exists to minimize those round trips. Two consequences:

- We cannot hold "every variant of every object" in memory core-style: memory and IO would
  explode.
- Switching variant is not an index change, it is a **reload**.
  `NetworkObjectIndex.ObjectCache.setResourcesToObjects()` reloads the new variant *with
  the same loading granularity as the previous one*: it synthesizes what had been loaded so
  far (`ONE` / `SOME` / `ALL` per object), takes the highest granularity, and performs a
  single bulk load at that level instead of N unit calls. This IO optimization has no
  equivalent in core and is intrinsically "one current variant at a time".

Copying core's per-variant state arrays is therefore not an option. The design must work
*with* the cache/IO system, not around it.

## 3. Architecture reminder: the four client layers

The client stack is assembled in `NetworkStoreService.createStoreClient`:

```
[PreloadingNetworkStoreClient]   <- optional, per PreloadingStrategy
        |
 CachedNetworkStoreClient
        |
 BufferedNetworkStoreClient
        |
 RestNetworkStoreClient  ->  RestClientImpl (HTTP to network-store-server)
```

| Layer | Role | State |
|---|---|---|
| `RestNetworkStoreClient` | the real IO; translates calls to HTTP requests | stateless |
| `BufferedNetworkStoreClient` | buffers **writes** (create/update/remove) per `(networkUuid, variantNum)` in `CollectionBuffer`s, sent on `flush()` | write buffers |
| `CachedNetworkStoreClient` | **read** cache of loaded resources, keyed by `(networkUuid, variantNum)` via `NetworkCollectionIndex` | read cache |
| `PreloadingNetworkStoreClient` | bulk-preloads whole collections per strategy (`COLLECTION`, `COLLECTION_AND_BUS_VIEW`) | preloaded-types tracking |

Key observation: **all four layers are already keyed by `(networkUuid, variantNum)`** — the
bottom of the stack can hold several variants at once. But they all rely on non-thread-safe
`HashMap`/`LinkedHashMap`, and *reads mutate them* (lazy loading, preloading, buffers).

Above the stack, the object layer is single-variant by construction:

- `NetworkObjectIndex.workingVariantNum` is one shared `int`;
- each `AbstractIdentifiableImpl` holds **one** `Resource` (the current variant's state);
  `setWorkingVariantNum` rewrites the resource of every live object via
  `setResourcesToObjects()`;
- the 21 `ObjectCache`s (one per equipment type) are plain `LinkedHashMap`s.

## 4. Chosen design: per-thread variant context over a shared thread-safe client stack

Do not touch the "one object = one current `Resource`" model. Instead, duplicate the
**context** per thread (working variant + object caches + network resource), and let the
shared client stack — already variant-keyed — absorb the IO so N threads do not cost N
times more HTTP.

```
                    Thread A                Thread B
                 (variant 0)             (variant 2)
                       |                       |
   NetworkImpl (shared, single) -- ThreadLocal<VariantContext>
                       |                       |
        +--------------+------+   +-----------+----------+
        | context A:          |   | context B:           |
        | workingVariantNum=0 |   | workingVariantNum=2  |
        | ObjectCaches (A)    |   | ObjectCaches (B)     |
        | LoadImpl@1, ...     |   | LoadImpl@2, ...      |
        +--------------+------+   +-----------+----------+
                       +-----------+----------+
              SHARED client stack, made thread-safe
     Preloading -> Cached -> Buffered -> Rest   (already keyed by (uuid, variantNum))
```

### Why it fits

In `NetworkObjectIndex` all the mutable state is already grouped: `workingVariantNum` and
the 21 `ObjectCache` fields, whose loading lambdas capture `workingVariantNum` at call
time. Extracting that block into a `VariantContext` class resolved through a `ThreadLocal`
(only when the flag is on) leaves the rest of the object layer untouched:

- `AbstractIdentifiableImpl` keeps its single `resource` field: each wrapper now belongs to
  one thread's cache, nobody else touches it.
- `setResourcesToObjects()` and the `ONE/SOME/ALL` granularity logic stay as-is: a thread
  switching variants reloads *its own* wrappers, in *its own* context.

IO stays contained because the client stack is shared: when thread B loads variant 2, if
any thread already loaded it, `CachedNetworkStoreClient` serves everything from its
`(uuid, 2)` cache — zero HTTP. The per-thread overhead reduces to wrapper creation (thin
objects pointing at the `Resource` instances of the shared cache), not re-fetching
persisted data.

### Changes per layer

1. **Client stack thread-safety** (prerequisite, mostly mechanical):
   - `RestNetworkStoreClient`: stateless, `RestTemplate` is thread-safe — nothing.
   - `BufferedNetworkStoreClient`: synchronize `CollectionBuffer` appends and `flush()`;
     make `NetworkCollectionIndex.getCollection` (a `computeIfAbsent` on `LinkedHashMap`)
     concurrent. Threads writing to different variants land in different buffers.
   - `CachedNetworkStoreClient`: `NetworkCollectionIndex` + `CollectionCache` internals +
     `variantsInfosByNetworkUuid` + the `MutableInt` call counter — concurrent maps /
     fine-grained locks per collection. Watch `removeCollection`/`applyToCollection`
     racing readers.
   - `PreloadingNetworkStoreClient`: `cachedResourceTypes` + prevent double preloads
     (lock per `(uuid, variantNum, resourceType)`).

2. **`NetworkObjectIndex`: extract `VariantContext`** —
   `{workingVariantNum, the 21 ObjectCaches, the network resource}` moves into an inner
   class. Flag off: a single instance, today's code path, zero behavior change. Flag on:
   `ThreadLocal<VariantContext>`, lazily initialized per thread with
   `workingVariantNum = -1` so a new thread must call `setWorkingVariant` first (same
   `Variant index not set` contract as core).
   Subtlety: `NetworkImpl` is itself an identifiable whose resource is rewritten on every
   variant switch — its resource belongs to the per-thread context too. This is done with
   two overridable storage hooks in `AbstractIdentifiableImpl` that `NetworkImpl` overrides
   to delegate to the index context.

3. **`VariantManagerImpl`: lift the stub** —
   `allowVariantMultiThreadAccess(true/false)` toggles the mode in the index, migrating the
   current state in both directions (enabling moves today's context into the calling
   thread's slot; disabling collapses the calling thread's context back to the single one).
   `removeVariant` must invalidate other threads' contexts pointing at the removed variant:
   lazy invalidation (epoch/version on the variant list, checked on access) rather than
   tracking threads.

4. **Writes, flush, listeners** —
   a thread's writes go to *its* variant's buffer, so threads on distinct variants do not
   interfere. `NetworkStoreService.flush(network)` flushes all variants at once and must be
   documented as a stop-the-world operation. `NetworkImpl.listeners` becomes a
   `CopyOnWriteArrayList` since notifications may come from several threads.

### Trade-offs (to validate with downstream consumers)

1. **Per-thread object identity.** `network.getLoad("l")` returns a different instance per
   thread. Identity is stable within a thread (what loadflow/security-analysis-style code
   needs). Rule to document: in multi-thread mode, each thread obtains its objects through
   the shared `Network`; equipment references must not be passed between threads. This
   deviates from core, where the same instance is shareable — THE point to validate with
   gridsuite usage before generalizing.
2. **Shared `Resource` instances on the same variant.** Two threads on the same variant get
   the same `Resource` objects from the shared cache: concurrent reads fine; a write in one
   is visible mid-read in the other. Same contract as core (distinct variants: supported;
   same-variant concurrent writes: not).
3. **Memory**: N threads × wrappers of the visited variant. Wrappers are thin (attributes
   live in the shared client cache), far below core-style per-variant arrays.
4. **Mode off = strictly today's behavior**, deliverable incrementally.

## 5. Delivery plan

| Step | Content | Risk |
|---|---|---|
| 1 | Thread-safety of the client stack (Cached/Buffered/Preloading + `NetworkCollectionIndex`) | mechanical, testable via multi-thread stress test on a mocked `RestClient` |
| 2 | `VariantContext` extraction in `NetworkObjectIndex` (pure refactor, no flag) | behavior must be strictly identical; full test suite |
| 3 | `ThreadLocal` switch + lift the stub + `removeVariant` invalidation + concurrent listeners | new semantics, gated behind the flag |
| 4 | Tests: port core's multi-variant tests (N threads pinned to distinct variants, value isolation) + "thread B benefits from thread A's cache" (count mocked Rest calls) | validates the central promise |

## Prototype status

Prototyped on this branch (steps 2 and 3, partially):

- [x] `VariantContext` extraction in `NetworkObjectIndex` — pure refactor, single default
  context when the flag is off.
- [x] Storage hooks in `AbstractIdentifiableImpl` + `NetworkImpl` routing its resource
  through the variant context.
- [x] `ThreadLocal<VariantContext>` mode + `allowVariantMultiThreadAccess` /
  `isVariantMultiThreadAccessAllowed` lifted in `VariantManagerImpl`.
- [x] Multi-thread read tests (`VariantMultiThreadAccessTest`): concurrent reads on distinct
  variants, `Variant index not set` contract for threads that did not select a variant,
  per-thread variant switching, enable/disable round trip.
- [x] Step 1 (client stack thread-safety):
  - `NetworkCollectionIndex` backed by a `ConcurrentHashMap`;
  - `CollectionCache` public methods synchronized per (network, variant) instance — threads
    on distinct variants stay parallel, same-variant accesses are serialized and a
    collection is loaded from the server only once (verified by
    `ClientCacheMultiThreadTest`);
  - `CollectionBuffer` methods synchronized per (network, variant) instance, id getters
    return snapshots (`CollectionBufferMultiThreadTest`);
  - `CachedNetworkStoreClient`: variants infos in a `ConcurrentHashMap` of
    `CopyOnWriteArrayList`, `getIdentifiable` call counters as `AtomicInteger`,
    identifiable-ids sets concurrent;
  - `PreloadingNetworkStoreClient`: per-(network, variant) resource-type sets guarded by
    their own monitor, held during the preload so a concurrent thread on the same variant
    waits instead of preloading twice.
- [x] `CopyOnWriteArrayList` for `NetworkImpl` listeners.
- [x] `removeVariant` cross-thread invalidation: a modification counter is bumped on each
  removal; a thread context revalidates its working variant (num AND id, to catch variant
  num reuse by a later clone) on its next access and falls back to the
  `Variant index not set` state if it disappeared. Caveat: `cloneVariant` with overwrite
  recreates the same (num, id), which revalidation cannot distinguish — variant management
  concurrent with reads of that same variant stays out of scope, as in core.
- [x] TCK `AbstractMultiVariantNetworkTest` re-enabled: `singleThreadTest`,
  `variantNotSetTest` and `variantSetTest` now run as inherited; `multiThreadTest` is
  overridden with an adapted version where each thread obtains its own equipment reference
  (per-thread identity contract, see trade-off 1); `multiVariantTopologyTest` stays
  overridden because it tests core's global-structure-across-variants model, which does not
  apply here (each variant is a full copy, structural changes are per variant) — a
  pre-existing modeling difference unrelated to multi-threading.
- [x] `NetworkStoreService.flush` documented as stop-the-world with respect to concurrent
  modifications.

## Downstream validation (gridsuite-shaped usage)

Validated on this branch with real-engine tests (`powsybl-open-loadflow` added as a test
dependency):

- **Security-analysis-shaped workflow** (`VariantMultiThreadWorkflowTest`): main thread
  clones one variant per contingency, worker threads pin their variant, apply the
  contingency (line disconnection) and a redispatch, then the main thread visits each
  variant to collect results — seeing the workers' modifications through the shared client
  cache — and removes the variants. Works end to end.
- **Parallel load flows on distinct variants**
  (`OpenLoadFlowMultiThreadVariantTest.parallelLoadFlowsOnDistinctVariants`): two threads
  each run a real open-loadflow AC load flow on their own variant of the same shared
  `Network`, concurrently, results isolated per variant. This is the case that today forces
  downstream services to copy the network to in-memory IIDM first.
- **Multi-thread security analysis**
  (`OpenLoadFlowMultiThreadVariantTest.multiThreadSecurityAnalysis`): open-loadflow with
  `threadCount = 2` **requires this feature and now works**. OLF's
  `ContingencyMultiThreadHelper` calls `allowVariantMultiThreadAccess(true)` itself, then
  each worker thread does `setWorkingVariant` and builds its own `LfNetwork` by reading the
  shared IIDM network — the exact per-thread pattern this design supports. Verified both
  ways: the same test on `main` fails with
  `Network store implementation does not support multi-thread access yet`.
  Two important consequences:
  - the flagship engine already follows the per-thread reference contract (workers never
    share identifiable references; each builds its own model from the shared `Network`),
    which supports the "document the contract" option over foreign-wrapper read-through;
  - OLF workers all pin the **same** variant, so this also exercises concurrent
    same-variant reads: per-thread wrappers over shared `Resource` instances served by the
    (synchronized) shared collection cache.
- **Differential test against the reference implementation**
  (`SecurityAnalysisImplComparisonTest`): the same multi-thread open-loadflow security
  analysis, on the same network (Eurostag with fixed current limits, N-1 on each of the two
  parallel lines), run on the in-memory powsybl-iidm-impl and on this implementation,
  produces the same results — statuses and limit violations compared after normalization
  (sorted, values rounded to 0.1 A). `powsybl-iidm-impl` is a test dependency for this;
  the test platform config pins `network: default-impl-name: NetworkStore` so that all
  other tests keep resolving `NetworkFactory.findDefault()` to this implementation.
- **Differential test on a bigger network** — the same comparison on the CGMES conformity
  small grid (115 buses, 173 lines, native operational limits), imported once in each
  implementation, N-1 on every line, `threadCount = 4`
  (`sameSecurityAnalysisResultsOnBothImplementationsOnCgmesSmallGrid`). The **load flow
  results are identical** on both implementations (statuses and flow values match to 0.1 A
  on every contingency), but the test is `@Disabled` because it caught two data-fidelity
  bugs — both also present on `main`, so pre-existing and unrelated to multi-threading:
  - **`ActivePowerControl` droop default (FIXED on this branch)**: the network store
    `ActivePowerControlAdderImpl` defaulted `droop` and `participationFactor` to `0.0`
    where the core implementation defaults them to `NaN` (= unset). A CGMES import that
    sets only the participation factor thus produced `droop = 0.0`, which open-loadflow
    interprets as "no participation capacity": the base load flow failed with *Failed to
    distribute slack bus active power mismatch* on the store network while converging on
    the in-memory one. Fixed by aligning the defaults on `NaN`.
  - **Temporary limits lost on CGMES import (FIXED on this branch)**: 269 of the 392
    temporary limits (TATL) of this grid were silently lost when importing into the
    network store implementation, so violations could be reported against the wrong limit.
    Root cause: the CGMES conversion accumulates the limits of a group in a single
    `LoadingLimitsAdder` cached in a `HashMap<OperationalLimitsGroup, ...>`
    (`LimitsMapping.getLoadingLimitsAdder`). The core implementation always returns the
    same `OperationalLimitsGroup` instance for a given group, so the cache hits; the
    network store implementation creates a new wrapper on every lookup, so every CGMES
    limit row got its own adder and each `add()` replaced the group limits written by the
    previous rows (last row wins, with `fixLimits` even inventing a permanent limit from
    the row's TATL). Fixed by defining `equals`/`hashCode` on
    `OperationalLimitsGroupImpl` over the wrapped group identity (owner, side, group id)
    so that wrappers from different lookups are interchangeable as map keys. The
    differential test now guards the imported temporary limit count on both
    implementations. Note the family resemblance with the per-thread reference trade-off:
    consumers may rely on wrapper identity, and this implementation does not always
    provide it even in single-thread use — worth a dedicated audit.
- **Pre-existing gap found, unrelated to multi-threading**: the implementation has no
  `ExtensionAdderProvider` for the `ReferenceTerminals` extension that open-loadflow writes
  by default (`OpenLoadFlowProvider.runAc` -> `ReferenceTerminals.reset`). Any load flow
  run against this implementation must set `writeReferenceTerminals(false)`, or the
  extension support should be added. Also noticed on the CGMES export comparison: the
  store network serializes without the `slackTerminal`, `cimCharacteristics`,
  `baseVoltageMapping` and `cgmesMetadataModels` extensions that the in-memory network
  carries.

### End-to-end validation against a real server

Also validated against a locally built network-store-server backed by PostgreSQL 16
(CGMES conformity small grid, all times on a 4-core container):

- the **multi-thread security analysis works over REST** end to end: ~1.3 s on first run
  (client cache filling from the server), ~0.6 s warm — the per-thread variant contexts
  read through the shared, synchronized client cache with a real HTTP/SQL backend;
- `cloneVariant` end to end costs ~68 ms with a warm client cache versus ~18 ms with a
  cold one: the client-side JSON deep copy of the cached collections accounts for about
  three quarters of the total clone cost, more than the server-side SQL copy itself
  (profiling showed ~94% of the client-side clone CPU is Jackson serialize/deserialize
  in `Resource.cloneResourcesToVariant`, called by both the cached and buffered layers);
- **operational caveat found**: with the default Spring request factory (JDK
  `HttpClient`), open-loadflow multi-thread security analysis deadlocks. The SA
  partitions run on the `ForkJoinPool` common pool and block on REST calls whose
  response completions also need common pool threads; with parallelism 3 (4 cores) all
  workers park and the responses are never delivered. Using a blocking request factory
  (e.g. `SimpleClientHttpRequestFactory`, or Apache HttpClient as deployed setups do)
  avoids it. Worth documenting for any consumer running multi-thread computations
  against the REST client.

Still to do downstream (cannot be done from this repository):

- Inventory gridsuite services for `allowVariantMultiThreadAccess`, in-memory network copy
  workarounds, and `parallelStream()` over network equipment (the sharpest trap of the
  per-thread reference contract: wrappers obtained on one thread and read from ForkJoinPool
  threads fail with `Variant index not set`).
- Canary: build this branch as a SNAPSHOT, bump one service (e.g. loadflow-server), run its
  suite plus a multi-variant scenario against a real network-store-server + database.
- Decide, based on real engine needs, between documenting the per-thread reference contract
  (with a clearer error when a wrapper is accessed from a foreign thread) and implementing
  foreign-wrapper read-through (resolve reads via the calling thread's context) for core
  parity on reads.
- [ ] Known pre-existing quirk kept as-is: `setWorkingVariantNum` does not reload the
  tie-line cache (`tieLineCache` missing from the `setResourcesToObjects` list).

## Evaluation of the OLF "copy mode" work in progress (`lfnetwork_copy` + `sa_mt_copy`)

Open-loadflow has a work-in-progress alternative for multi-thread security analysis:
instead of each worker thread rebuilding its own `LfNetwork` from the IIDM network
(`networkPerThreadMode=REBUILD`, the legacy behavior), the networks are built **once** on
the calling thread and each partition gets a lock-free in-memory deep copy
(`LfNetworkCopier`, `networkPerThreadMode=COPY`, the new default on that branch). The two
branches are stacked (`sa_mt_copy` contains `lfnetwork_copy`), so `sa_mt_copy` is the
combined feature. Evaluated against this implementation on the local
network-store-server + postgres stack (CGMES small grid, 173 line contingencies, 3 SA
threads; OLF branch is on powsybl-core 7.3.0-RC2, so the client stack was locally rebuilt
against that core version — a mechanical port, not committed).

Findings:

- **COPY mode works on network-store networks**: no fallback to rebuild
  (`canCopy` accepts the built networks, presolve succeeds), and the results are
  **identical** to single-thread and to REBUILD mode (normalized statuses + violations).
- **It still requires `allowVariantMultiThreadAccess`** (this feature): the helper turns
  the flag on unconditionally, and partition workers still call `setWorkingVariant` and
  still read the IIDM network during simulation (see next point). On `main` it fails
  exactly like REBUILD mode does.
- **It is not IIDM-free during the run phase**: OLF's `AbstractLfBranch.createLimits` is
  lazy — the first limit-violation check per partition reads
  `getAllSelectedOperationalLimitsGroups` from the IIDM branch, which on this
  implementation is a REST load (`.../branch/types/LINE/operationalLimitsGroup/selected`)
  from a worker thread. Two consequences:
  - the ForkJoinPool/JDK-HttpClient starvation deadlock documented above is **not** fixed
    by COPY mode: with the JDK request factory both modes hang at 3 threads on 4 cores
    (REBUILD during the per-thread builds, COPY at the first lazy limits load; thread dump
    shows the worker parked in `RestTemplate.doExecute` while holding the collection-cache
    monitor). The request-factory fix stays necessary regardless of mode.
  - a cheap improvement for the OLF branch: materialize branch limits on the calling
    thread before taking the copies (or copy the limits in `LfNetworkCopier`), which would
    make the run phase truly IIDM-free, remove the deadlock exposure and the need for
    worker-side `setWorkingVariant`. **Implemented and verified**
    (`sa_mt_copy_limits_prewarm` branch on the OLF fork): the SA presolver materializes
    every branch's limits caches (all limit types, both sides, disabled branches included)
    with the same limit-reduction inputs as the violation managers, and the
    `AbstractLfBranch` copy constructor shares the immutable caches. With that change the
    previously deadlocking JDK-factory multi-thread SA completes (~2.8 s), results stay
    identical to single-thread and REBUILD, and OLF's copy-mode tests pass. The
    request-factory fix is then defense in depth for REBUILD mode and other consumers.
- **Performance on this implementation**: warm, COPY is ~25% faster than REBUILD
  (267 ms vs 348 ms; single-thread reference 2.0 s). Cold, they are equivalent (~1.8 s
  each, both 20 REST loads): with the COLLECTION preloading strategy the first build
  fills the shared client cache, so REBUILD's redundant per-thread builds cost CPU, not
  REST. COPY's gain here is the saved rebuild CPU and the removal of the build lock; the
  gain grows with network size and thread count.
- **Strategic update — COPY mode no longer requires this feature for plain SA** (second
  commit on the OLF `sa_mt_copy_limits_prewarm` branch): once the run phase is IIDM-free,
  the only remaining worker-side IIDM readers are the actions (converted per partition
  network), the state monitors and the result extensions (branch results read nominal
  voltages from the terminals). Without those, OLF now skips `allowVariantMultiThreadAccess`
  and the worker-side `setWorkingVariant` entirely. Verified: a 3-thread COPY-mode SA runs
  on an **unmodified network-store main** client with results identical to single-thread
  (0.38 s vs 1.47 s warm), while REBUILD mode still throws the stub exception. A third OLF
  commit then removed the last worker-side IIDM reads for **all SA use cases**: branch
  nominal voltages and bus voltage level ids cached at build, bus-breaker mappings and
  violation locations materialized before the copies, load action power shifts precomputed
  on the calling thread. Verified on unmodified network-store main with state monitors,
  result extensions and an operator strategy (load action + terminals connection action):
  results identical to single-thread (0.45 s vs 2.3 s). A fourth OLF commit extended the
  contract to the **multi-thread AC sensitivity analysis**: its last worker-side IIDM
  reader was per-partition factor resolution (`readAndCheckFactors` — branch/injection
  lookups, injection-to-bus bus-view navigation, GLSK expansion); resolution is partition
  independent, so it now runs once on the calling thread and the resolved factors are
  rebound onto each copy by element id only, letting the sensitivity COPY path also skip
  `allowVariantMultiThreadAccess`. Verified on unmodified network-store main: 40 factors ×
  173 contingencies (6960 values), 3 threads, values identical to single-thread, no
  deadlock even with the JDK http factory (0.35 s vs 1.26 s). Consequences for this
  branch: this feature remains required for REBUILD-mode SA and sensitivity, and for any
  downstream per-thread variant workflow (parallel load flows on distinct variants, the
  gridsuite clone-per-contingency pattern) — but every OLF COPY-mode scenario (security
  analysis and sensitivity) now works before this branch is merged.
