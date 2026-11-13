#!/usr/bin/env bash
set -euo pipefail
task_use_root="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")/../.." && pwd)"
task_maven="${USE_MAVEN:-}"
if [[ -z "$task_maven" ]]; then
  if command -v mvn >/dev/null 2>&1; then task_maven=mvn
  elif [[ -x /run/media/qbert/1C8099B8809998BA/env/apache-maven-3.9.9/bin/mvn ]]; then
    task_maven=/run/media/qbert/1C8099B8809998BA/env/apache-maven-3.9.9/bin/mvn
  else echo 'Set USE_MAVEN to Maven 3.9.x executable.' >&2; exit 1; fi
fi
task_module="$task_use_root/use-plugin/modules/state-verification"
if [[ ! -s "$task_module/target/runtime-classpath.txt" || "${1:-}" == --build ]]; then
  if [[ "${1:-}" == --build ]]; then shift; fi
  "$task_maven" -B -f "$task_use_root/pom.xml" -pl use-plugin/modules/state-verification -am -Dmaven.test.skip=true install
  "$task_maven" -B -f "$task_module/pom.xml" dependency:build-classpath -Dmdep.includeScope=runtime -Dmdep.outputFile=target/runtime-classpath.txt
fi
task_classpath="$task_module/target/classes:$(cat "$task_module/target/runtime-classpath.txt")"
task_example="$task_use_root/use-plugin/examples/auction"
if [[ "${1:-}" == --counter ]]; then
  shift
  task_counter="$task_use_root/use-plugin/examples/state-only-counter"
  exec java -cp "$task_classpath" org.tzi.use.state.StateWorkbenchMain \
    "$task_counter/counter.jcm" "$task_counter/attribute-mapping.xml" \
    "$task_counter/counter.csl" "$task_counter/counter.istar" "$@"
fi
if [[ "${1:-}" == --project ]]; then
  shift
  if [[ $# -lt 4 ]]; then echo 'Usage: --project project.jcm mapping.xml model.csl model.istar [--headless] [--seconds=N]' >&2; exit 1; fi
  exec java -cp "$task_classpath" org.tzi.use.state.StateWorkbenchMain "$@"
fi
exec java -cp "$task_classpath" org.tzi.use.state.StateWorkbenchMain \
  "$task_example/auction.jcm" "$task_example/state-only/attribute-mapping.xml" \
  "$task_example/state-only/auction.csl" "$task_example/state-only/auction.istar" "$@"
