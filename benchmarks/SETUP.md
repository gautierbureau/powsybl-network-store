# Benchmark environment setup

End-to-end runbook to reproduce the local stack these benchmarks run against:
postgres + `powsybl-network-store-server`, the client/OLF build under test in
the local `.m2`, a network loaded into the store, and the `copybench` runner.
See `README.md` for the reference description of each piece; this is the
step-by-step.

## 0. Prerequisites

- JDK 21, Maven 3.9+
- PostgreSQL 16 (server), `psql`
- Python 3 (for `delay_proxy.py`)
- Local clones of the three repos:
  - `powsybl-network-store` (this repo — client under test)
  - `powsybl-network-store-server` (the REST + postgres server)
  - `powsybl-open-loadflow` (the load flow / security analysis engine)

## 1. Postgres

The server persists into a database named `iidm`.

```
# fresh data dir (skip if you already have one)
initdb -D /var/lib/postgresql/pgdata
pg_ctl -D /var/lib/postgresql/pgdata -l /var/lib/postgresql/pg.log start
createuser -s postgres 2>/dev/null; psql -c "ALTER USER postgres PASSWORD 'postgres';"
createdb -O postgres iidm
```

The server runs Flyway on boot, so the schema is created automatically the
first time it connects. To restart an existing instance:

```
pg_ctl -D /var/lib/postgresql/pgdata -l /var/lib/postgresql/pg.log start
psql -d iidm -c "select count(*) from network"   # sanity check
```

## 2. network-store server (:8080)

Build the server fork once, then run the executable jar against postgres:

```
cd powsybl-network-store-server
mvn -q -DskipTests install
java -jar network-store-server/target/powsybl-network-store-server-*-exec.jar \
     --spring.datasource.url=jdbc:postgresql://localhost:5432/iidm \
     --spring.datasource.username=postgres \
     --spring.datasource.password=postgres
# wait ~10s, then: curl -s -o /dev/null -w '%{http_code}\n' http://localhost:8080/v1/networks  -> 200
```

## 3. Client + OLF into `.m2`

`copybench` resolves the client and OLF from the local `.m2`, so install the
versions you want to measure. Two cases:

**a) plain build** (just the branch as-is):

```
cd powsybl-network-store && mvn -q -DskipTests install
cd ../powsybl-open-loadflow && mvn -q -DskipTests install
```

**b) overlay** (measure several perf branches together, as in the multi-thread /
copy-mode work). Build a throwaway tree = the feature branch with the perf
branches cherry-picked on top, plus `core-7.3-local-port.patch` when the OLF
branch is on powsybl-core 7.3 (it aligns the client's core version with OLF so
they share one classpath). Install it, **never commit it**:

```
git worktree add --detach /tmp/overlay <feature-branch>
cd /tmp/overlay
git apply .../benchmarks/core-7.3-local-port.patch          # only for the 7.3 OLF branch
git cherry-pick <perf-branch-1> <perf-branch-2> ...          # resolve conflicts toward the perf change
mvn -q install -Dmaven.test.skip=true -Dcheckstyle.skip      # test sources need the unpatched core, so skip them
# then install the OLF branch under test the same way
```

Keep the model / iidm-impl / client jars in `.m2` in lockstep — a stale model
jar against a newer iidm-impl gives `NoClassDefFoundError` (e.g. on a new
extension attributes class). When in doubt, `rm -rf` the three
`1.47.0-SNAPSHOT` dirs and reinstall the whole tree.

## 4. Load a network → get a UUID

Small CGMES grid (self-contained, no external file):

```
cd benchmarks/importer
mvn -q package dependency:build-classpath -Dmdep.outputFile=cp.txt
java -cp target/importer-1.0.jar:$(cat cp.txt) importer.ImportMain   # prints UUID=...
```

Large network (scale test), e.g. PEGASE 13659 from a MATPOWER `.m` case — powsybl
reads the binary `.mat`, so convert first (see `README.md`):

```
java -cp target/importer-1.0.jar:$(cat cp.txt) importer.MToMat case13659pegase.m case13659pegase.mat
java -cp target/importer-1.0.jar:$(cat cp.txt) importer.ImportFile case13659pegase.mat   # prints UUID=...
```

## 5. Run copybench

```
cd benchmarks/copybench
mvn -q package dependency:build-classpath -Dmdep.outputFile=cp.txt
java -cp target/copybench-1.0.jar:$(cat cp.txt) \
     copybench.Main <UUID> <threads> <simple|jdk> <mode> [strategy] [lineCount]
# e.g. clone-per-contingency at scale, 3 threads, computation preloading, 50 contingencies:
java -cp target/copybench-1.0.jar:$(cat cp.txt) \
     copybench.Main <UUID> 3 simple clonelf computation 50
```

Modes and flags are documented in `README.md` (`all`, `verify`, `verifyfull`,
`sensi`, `clonelf`, `clonelfflush`).

## 6. Latency (optional but important)

On pure localhost the REST round trips are free, so the preloading / bundle /
structural-clone gains are within noise. Put the delay proxy in front of the
server to model a realistic RTT, and point copybench at it:

```
python3 benchmarks/delay_proxy.py 0.005          # 5 ms per request, :8081 -> :8080
# then:
STORE_URL=http://localhost:8081 java -cp ... copybench.Main <UUID> 3 simple clonelf computation 50
```

`delay_proxy.py 0.005 block` additionally 404s the `/collections` and
`/bulk-update` endpoints, to exercise the client's fallback on an old server.

## Gotchas

- **`.mat` vs `.m`** — powsybl's MATPOWER importer reads the binary `.mat`, not
  the `.m` script; use `MToMat` to convert.
- **PEGASE convergence** — the raw 13659-bus case does not fully converge under
  default OLF parameters (a bus drops to ~0.06 pu). Harmless for the clone /
  infra timings (the digest records the solve status); tune the LF parameters if
  you want convergence-clean numbers.
- **Leftover variants** — the clone modes tag variants with a unique run id and
  remove them; if a run is killed mid-way, stale variants can remain. Re-import
  or delete them via the REST API.
- **Never commit the overlay** — it is a throwaway `.m2` build tree, not part of
  any branch.
