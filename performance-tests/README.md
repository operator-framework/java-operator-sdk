# Performance Tests

Performance related tests of the SDK. All of them are **skipped by default**; activate them with the
`performance-tests` profile.

| module      | what it measures                                              | needs a cluster |
|-------------|---------------------------------------------------------------|-----------------|
| `jmh`       | JMH micro-benchmarks and in-process throughput tests          | no              |
| `e2e`       | end-to-end reconciliation of a real operator on a real cluster | yes             |
| `reporting` | records the results of the above so they can be compared      | -               |

## `jmh`

JMH micro-benchmarks (`src/main/java`) and in-process throughput tests (`src/test/java`) for hot
paths of the core.

Run the throughput tests:

```shell
./mvnw verify -P performance-tests -pl performance-tests/jmh -am
```

Run the benchmarks (the shaded jar is built by a plain `install`):

```shell
./mvnw install -DskipTests
java -jar performance-tests/jmh/target/benchmarks.jar
```

The jar takes the same arguments as JMH itself, it only wraps `org.openjdk.jmh.Main` to also record
the scores as results, see `BenchmarkRunner`.

## `e2e`

Tests running a real operator against a real Kubernetes cluster, measuring end-to-end reconciliation
throughput. They use the current kube context, so a cluster (minikube, kind, ...) has to be
available.

```shell
./mvnw verify -P performance-tests -pl performance-tests/e2e -am
```

Scenario size can be tuned per test, see the javadoc of the individual tests, e.g.:

```shell
./mvnw verify -P performance-tests -pl performance-tests/e2e -am \
  -Dperformance.resourceCount=1000 -Dperformance.timeoutSeconds=600
```

## `reporting`

Records the measurements of the other two modules as JSON, so that the numbers of different commits
can be compared and visualized. A test opts in by being annotated with `@PerformanceTest`, and
receives a `PerformanceTestResults` parameter to add measurements to. The duration of the test method
itself is always recorded, results of failed tests are not recorded at all.

```java
@PerformanceTest(type = PerformanceTest.END_TO_END)
class SomethingE2E {

  @Test
  void measuresSomething(PerformanceTestResults results) {
    results.param("resourceCount", 100).recordElapsed("create", start, end);
  }
}
```

Results are written below `target/performance-results` of the module, override with
`-Dperformance.results.dir=...`.

### How results are stored

The `Performance Tests` workflow runs on every push to `main` and `next`, collects the results of all
jobs and commits them to the **`performance-test-results`** branch, which holds nothing but results:

```
performance-tests/results/
├── index.json                                    all runs, ordered oldest to newest commit
└── 20260825T070249Z-b2bace4/                     <commit timestamp>-<short commit>
    ├── run.json                                  commit, branch and timestamps of the run
    ├── jmh/
    │   └── <benchmark class>.<method>.json
    ├── in-process/
    │   └── <test class>.<test method>.json
    └── e2e/
        └── <test class>.<test method>.json
```

A run directory is named after the commit, prefixed with the **commit** timestamp (not the time the
tests ran) so that runs sort chronologically without consulting the git history, and so that a re-run
of a commit updates its results in place. `index.json` additionally lists the runs in order with
their commit, branch and the categories of tests that produced results, which is what tooling should
read instead of listing directories. It is regenerated from the run directories after every push:

```shell
java -cp performance-tests/reporting/target/performance-results-tools.jar \
  io.javaoperatorsdk.operator.performance.results.ResultsIndexer performance-tests/results
```

The branch is created by the workflow on its first run and needs no manual setup.
