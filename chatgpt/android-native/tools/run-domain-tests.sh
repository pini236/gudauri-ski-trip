#!/usr/bin/env bash
# SDK-independent verification of the real Kotlin domain sources and JUnit tests.
# Uses the same Kotlin and org.json versions as the Android project's dependencies.
# No test doubles for org.json, no website writes, and no bundled third-party binaries.
set -euo pipefail

task_project_dir="${GUDAURI_TEST_PROJECT:-$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)}"
task_kotlin_home="${GUDAURI_KOTLIN_HOME:?Set GUDAURI_KOTLIN_HOME to the Kotlin 2.2.20 distribution}"
task_gradle_lib="${GUDAURI_GRADLE_LIB:?Set GUDAURI_GRADLE_LIB to Gradle 8.13/lib for JUnit and Hamcrest}"
task_json_source="${GUDAURI_JSON_SOURCE:?Set GUDAURI_JSON_SOURCE to the JSON-java 20250517 checkout}"
task_java="${GUDAURI_JAVA:-java}"
task_output_dir=$(mktemp -d /tmp/gudauri-domain-tests.XXXXXX)
mkdir -p "$task_output_dir/json" "$task_output_dir/tests"

"$task_java" -m jdk.compiler/com.sun.tools.javac.Main --release 17 -encoding UTF-8 \
    -d "$task_output_dir/json" "$task_json_source"/src/main/java/org/json/*.java

task_classpath="$task_kotlin_home/lib/kotlin-stdlib.jar:$task_gradle_lib/junit-4.13.2.jar:$task_gradle_lib/hamcrest-core-1.3.jar:$task_output_dir/json"
mapfile -t task_domain_sources < <(rg --files "$task_project_dir/app/src/main/java/com/pini/gudauri/data" | rg -v '/MountainViewModel.kt$')
mapfile -t task_test_sources < <(rg --files "$task_project_dir/app/src/test/java/com/pini/gudauri/data" -g '*.kt')
"$task_java" -cp "$task_kotlin_home/lib/*" org.jetbrains.kotlin.cli.jvm.K2JVMCompiler \
    -no-stdlib -no-reflect -jvm-target 17 -classpath "$task_classpath" \
    -d "$task_output_dir/tests" "${task_domain_sources[@]}" "${task_test_sources[@]}"

task_test_classes=()
for task_source in "${task_test_sources[@]}"; do
    task_name="${task_source##*/}"
    task_test_classes+=("com.pini.gudauri.data.${task_name%.kt}")
done
"$task_java" -Dgudauri.dataDir="$task_project_dir/../../site/data" \
    -cp "$task_classpath:$task_output_dir/tests" org.junit.runner.JUnitCore "${task_test_classes[@]}"
printf 'Compiled domain test classes: %s\n' "$task_output_dir"

# This does not compile Compose, build an APK, or replace device/instrumentation tests.
