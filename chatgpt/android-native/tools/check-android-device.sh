#!/usr/bin/env bash
# Read-only readiness checks: a stale boot flag alone does not mean Android works.
set -euo pipefail
if [[ $# != 1 || -z "$1" ]]; then
    printf 'Usage: bash tools/check-android-device.sh <adb-serial>\n' >&2
    exit 2
fi
task_device=$1
task_adb=(adb -s "$task_device")
check_device() {
    local task_label=$1 task_expected=$2
    shift 2
    local task_output
    if ! task_output=$(timeout 10 "${task_adb[@]}" "$@" 2>&1); then
        printf 'NOT READY: %s: %s\n' "$task_label" "$task_output" >&2
        exit 1
    fi
    task_output=${task_output//$'\r'/}
    if [[ "$task_output" != "$task_expected" ]]; then
        printf 'NOT READY: %s: %s\n' "$task_label" "$task_output" >&2
        exit 1
    fi
}
check_device 'adb transport' device get-state
check_device 'completed boot' 1 shell getprop sys.boot_completed
for task_service in activity package window; do
    check_device "$task_service service" "Service $task_service: found" shell service check "$task_service"
done
# Exercise the service, not merely its registration with ServiceManager.
task_current_user=$(timeout 10 "${task_adb[@]}" shell am get-current-user)
task_current_user=${task_current_user//$'\r'/}
if [[ ! "$task_current_user" =~ ^[0-9]+$ ]]; then
    printf 'NOT READY: ActivityManager did not return a current user\n' >&2
    exit 1
fi
task_framework=$(timeout 10 "${task_adb[@]}" shell cmd package path android)
task_framework=${task_framework//$'\r'/}
if [[ "$task_framework" != package:*/framework-res.apk ]]; then
    printf 'NOT READY: PackageManager did not resolve the Android framework\n' >&2
    exit 1
fi
# A boot-time SystemUI error can stay visible while Compose semantics still work.
# Never treat tests behind that dialog as a healthy human-facing device session.
task_processes=$(timeout 15 "${task_adb[@]}" shell dumpsys activity processes)
if [[ "$task_processes" =~ m(Crashing|NotResponding)=true ]]; then
    printf 'NOT READY: an Android process is marked crashing or not responding. Inspect the system error dialog.\n' >&2
    exit 1
fi
printf 'READY: %s; boot, system services and binder requests responded; no process crash/ANR flags.\n' "$task_device"
printf 'This is a preflight check, not a substitute for instrumentation tests.\n'
