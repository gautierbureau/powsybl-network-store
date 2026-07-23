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
| `importer/` | `ImportMain` imports the CGMES small grid into the local store and prints its UUID; `ImportFile` imports any network file (e.g. a MATPOWER `.mat`) and prints the UUID + a size summary; `MToMat` converts a MATPOWER `.m` script to the `.mat` binary powsybl reads; `BenchPreload` times the cold preload (bundle endpoint vs per-collection) + a single-thread SA digest. |
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
