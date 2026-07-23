# network-store benchmarks

Throwaway harnesses used to measure the multi-thread / variant work on the
network-store client and the open-loadflow COPY mode against a **real local
stack** (postgres + `powsybl-network-store-server`), not just unit tests.
Standalone Maven modules (`groupId=scratch`), deliberately kept out of the
reactor so they never affect the library build.

## Pieces

| path | what it does |
|---|---|
| `copybench/` | the main runner. Reads a stored network, then benchmarks / verifies OLF security analysis, sensitivity, and the gridsuite clone-per-contingency pattern. |
| `importer/` | store / file conversion tools (see below): `ImportMain`, `ImportFile`, `ExportFile`, `MToMat`, `ToXiidm`, `BenchPreload`. |

### importer tools

| tool | args | does |
|---|---|---|
| `ImportMain` | — | import the bundled CGMES small grid into the store, print `UUID=...`. |
| `ImportFile` | `network-file` | import any network file (e.g. a MATPOWER `.mat` or an `.xiidm`) into the store, print the UUID + a size summary. |
| `ExportFile` | `uuid out.xiidm` | read a stored network by UUID and write it as XIIDM (for external post-processing). |
| `ToXiidm` | `in out.xiidm` | read a network file in memory (no store) and write it as XIIDM. |
| `MToMat` | `in.m out.mat [name]` | convert a MATPOWER `.m` script to the `.mat` binary powsybl reads. |
| `BenchPreload` | `uuid` | time the cold preload (bundle endpoint vs per-collection) + a single-thread SA digest. |
| `delay_proxy.py` | a TCP proxy on `:8081 -> :8080` that adds a fixed per-request latency (to model network RTT on a localhost stack) and can `block` the `/collections` and `/bulk-update` endpoints to simulate an old server (exercises the client 404 fallback). Also logs each request line. |
| `core-7.3-local-port.patch` | ports the network-store tree to the powsybl-core version OLF is built against (7.3), so the client and OLF can share one classpath in a local overlay build. Apply only when building the overlay, never commit it. |

## Local stack

1. **Postgres** with a database `iidm` (the server's default schema/flyway runs on boot).
2. **Server**: build and run `powsybl-network-store-server` against that postgres on `:8080`.
3. **Client + OLF in `.m2`**: `mvn -q -pl network-store-model,network-store-iidm-impl,network-store-client -am install -DskipTests`
   (add `git apply benchmarks/core-7.3-local-port.patch` first if the OLF branch under test is on core 7.3), and `mvn -q install -DskipTests` on the open-loadflow branch under test.
4. **Load a network**: `mvn -q -f benchmarks/importer/pom.xml package && java -cp benchmarks/importer/target/importer-1.0.jar:$(cat benchmarks/importer/cp.txt) importer.ImportMain` → prints `UUID=...`.

Point the runner at another server with `STORE_URL` (e.g. the delay proxy):
`STORE_URL=http://localhost:8081`.

### Loading a large network (scale test)

powsybl's MATPOWER importer reads the binary `.mat` format, not the `.m` MATLAB
script the cases are usually distributed as. `MToMat` converts one to the other
by parsing the bus/gen/branch matrices into powsybl's own `MatpowerModel` and
writing it with `MatpowerWriter` (so the output is exactly what the importer
reads). For the PEGASE 13659-bus case:

```
# download the .m (e.g. from MATPOWER/matpower data/ or power-grid-lib/pglib-opf)
java -cp benchmarks/importer/target/importer-1.0.jar:$(cat benchmarks/importer/cp.txt) \
     importer.MToMat case13659pegase.m case13659pegase.mat
java -cp benchmarks/importer/target/importer-1.0.jar:$(cat benchmarks/importer/cp.txt) \
     importer.ImportFile case13659pegase.mat        # prints UUID=...
```

Measured on the 13659-bus case: a structural variant clone of the whole network
is ~0.1 s, the cold computation-strategy preload ~1.6 s; the clone-per-contingency
wall time is then dominated by the AC load flow (~3 s per solve), not the store.

### Enhancing a raw case into a "real" network

A MATPOWER import is bus/branch topology with minimal metadata. To turn it into a
richer network — operational limit groups and node-breaker topology — pipe it
through the tools in the companion **`gautierbureau/test2`** repo (a separate
picocli CLI, powsybl-core 7.3.x; build its shaded jar with `mvn -DskipTests
package`). The importer here handles the store/XIIDM ends:

```
# 1. stored network (or .mat) -> XIIDM
java -cp importer.jar:$(cat importer/cp.txt) importer.ExportFile <UUID> pegase.xiidm
#    (or, straight from the .mat without the store: importer.ToXiidm pegase.mat pegase.xiidm)

# 2. operational limit groups: a LOADFLOW_BASED group per branch, permanent +
#    temporary limits sized from an AC load flow, selected as the active group
java -cp test2-shaded.jar com.example.transporter.AddCurrentLimits -i pegase.xiidm -o pegase_lim.xiidm

# 3. bus-breaker -> node-breaker: one busbar section per bus, feeders on breaker bays
java -cp test2-shaded.jar com.example.transporter.ConvertToNodeBreaker -i pegase_lim.xiidm -o pegase_nb.xiidm

# 4. (optional) generator/transformer completion: reactive limits, ratio tap
#    changers, energy source, active power control
java -cp test2-shaded.jar com.example.transporter.CompleteNetwork -i pegase_nb.xiidm -o pegase_full.xiidm

# 5. back into the store
java -cp importer.jar:$(cat importer/cp.txt) importer.ImportFile pegase_nb.xiidm   # prints a new UUID
```

Notes:
- **IIDM version** — test2 writes IIDM schema 1.17 (powsybl-core 7.3.x); the
  importer module must be on the same core line (its BOM imports
  `powsybl-core:7.3.0-RC2`), or `Network.read` / the store import reports
  "No importer found" on the `.xiidm`.
- **Limit sizing needs a converged base case** — PEGASE 13659 converges at the
  base case (a bus only drops out of range under a contingency), so the limits
  are sized correctly. On a case that does not converge, use `AddCurrentLimits
  --min-current` as the fallback.
- The node-breaker conversion **preserves** the operational limit groups, so
  limits-then-topology (as above) is fine. On the 13659-bus case the result is
  ~118k switches + 13659 busbar sections, with a selected limit group on ~14.5k
  of the 14.7k lines — a good target for the switch collection and the
  selected-limits loading paths.

## Running

```
mvn -q -f benchmarks/copybench/pom.xml package dependency:build-classpath -Dmdep.outputFile=cp.txt
java -cp benchmarks/copybench/target/copybench-1.0.jar:$(cat benchmarks/copybench/cp.txt) \
     copybench.Main <networkUuid> <threads> <simple|jdk> <mode> [strategy] [lineCount]
```

- `threads` – worker thread count for the multi-thread runs.
- `simple|jdk` – the Spring REST request factory. `jdk` (HttpURLConnection) used to deadlock on any worker-side REST read; it is the strict check that the run phase never touches IIDM from a worker.
- `mode`:
  - `all` – single-thread reference, then REBUILD vs COPY security analysis (2 warm runs each), with timings and a bit-identical result check.
  - `verify` / `verifyfull` – COPY vs single-thread results must match; `verifyfull` adds monitors, result extensions and an operator strategy with actions (everything that used to force worker-side IIDM reads).
  - `sensi` – COPY-mode AC sensitivity vs single-thread, values must match.
  - `clonelf` – gridsuite clone-per-contingency: per line, clone the variant, open the line, run a load flow, digest, remove the variant; sequential vs `threads`, results must match. `-Dclonelf.trace` prints per-clone timing.
  - `clonelfflush` – same but each worker `flushWorkingVariant`s the load-flow results to the server before removing its variant (exercises the concurrent buffered write path).
- `strategy` (5th arg) – preloading strategy for the read: default `COLLECTION`, or `busview` / `computation`.
- `lineCount` (6th arg, clonelf modes) – cap the number of contingencies.

## delay proxy

```
python3 benchmarks/delay_proxy.py 0.005          # 5 ms per request, on :8081 -> :8080
python3 benchmarks/delay_proxy.py 0.005 block    # also 404 the /collections and /bulk-update endpoints
```

Then run copybench with `STORE_URL=http://localhost:8081`. The added latency is
what makes the effect of the preloading / bundle / structural-clone work visible;
on pure localhost the round trips are free and most of it is within noise.
