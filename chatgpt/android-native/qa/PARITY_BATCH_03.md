# Android parity batch 3 — browser readiness and native run profiles

Date: 2026-10-01. Read-only website baseline: `d0a2e88dbc7802a100fe997c8c3b350913ad80a1`.
This is a bounded implementation batch, not completion of the parity plan.

## Environment

After explicit user approval, the exact platform CA was found already trusted
under an existing nickname. Chromium's read-only sandbox mount, not a missing
certificate, prevented loading NSS. Normal approved browser access resolved it.
The full original reference suite passed **100/100**, no failures/skips, including
all four previously skipped 3D lift-label checks, with HTTPS checking enabled.
See [the complete diagnosis](../docs/CLOUD_ENVIRONMENT.md) and
`qa/reports/reference-browser-tls-verified.xml`.

The complete six-helper setup ran end to end, including the real HTTPS/WebGL
preflight; its unit/build/lint tasks reused previously verified outputs during
that environment refresh. Both installation and startup fields were saved and
read back from draft revision 8. This is not publication or a fresh-machine test.

## Native implementation

- Pure `RunProfile` samples the longest existing line downhill at approximately
  10 m spacing, preserving vertices without bridging disconnected geometry.
  Polygons and zero-length lines do not receive invented profiles.
- Local slope uses the source's approximately 40 m window; the steep stretch
  uses approximately 100 m, with its minimum 60 m tail policy. Headline statistics
  still cover all original lines, not just the displayed profile.
- Exact four slope colours, exclusive 15°/25°/30° boundaries, an explicit native
  legend, slope-coloured chart, shaded steep stretch and selection marker.
- Distance-based selection with numeric distance, height and local slope. A
  native accessible slider updates the same chart/readout; its physical axis is
  left-to-right while surrounding Hebrew remains RTL. Saved state is run-specific.
- Labels explain model uncertainty, longest-line length, disconnected sections
  and the difference between slope colour and official difficulty.

## Actual verification

**37 unit tests passed**, no failures/errors/skips: the previous 29 plus 8 profile
cases. New coverage includes sampling, distance, downhill direction, areas,
duplicate/zero-length vertices, short/flat lines, finite samples, endpoints,
exact colour boundaries and invalid positions.

Five real expected profiles were independently evaluated from the unchanged
website `sampleLine`/`runProfile`, with its actual DEM/projection. Kotlin matches
sample count, length, first/middle/last heights, first slope, steep indices and
grade within explicit floating-point tolerances. Fixtures cover Tatra 2, Kudebi 1,
Zuma, Sportuli 2 and Pirveli; the area-only entry retains no profile.

Real Compose debug and instrumentation APKs built; debug lint passed. An initial
compile exposed missing imports; these were fixed and all affected tasks rerun.
No dependency, source geometry or test timeout was changed.

The expanded device suite passed **8/8 together**, no failures/errors/skips, on
AOSP API 30 / Android 11, 720×1600 / density 280, software CPU emulation and
SwiftShader. The actual Gradle task finished successfully in 7 min 56 sec.
The new case checks exact start/end readings using the slider's accessibility
progress action, then restores the start; all seven prior regression cases were
rerun against this binary. XML is retained in `qa/reports/api30-batch3-full-run.xml`.

Subsequent pixel inspection found a stale **SystemUI not responding** dialog
still covering the emulator. Events place the service ANR at 15:33:08, more than
an hour before this suite started at 16:38:58; the SystemUI process retained
`mNotResponding=true`. The runner's eight passes above are real, but Compose
semantics do not establish an unobstructed human-facing screen. Screenshot
evidence is retained in `qa/reports/api30-systemui-dialog.png`; the original XML
is also retained as `qa/reports/api30-batch3-before-systemui-guard.xml`.

The dialog's normal **Wait** action cleared it; the process was no longer marked
unresponsive and window focus returned to the launcher. No watchdog, security
check, system app or timeout was disabled. The preflight now rejects actual
process crash/ANR flags. Instrumentation additionally checks those flags and the
real active accessibility window before and after every case, so an obstructed
Compose screen cannot be counted as healthy. The strengthened run below failed;
the earlier eight-case result is not clean-system validation.

The first strengthened rerun **did not pass**: 1 passing case, then the profile
flow aborted with `keyDispatchingTimedOut`; six cases did not execute. Activity
Manager recorded a 5002 ms focus-event timeout for MainActivity. XML is retained
in `qa/reports/api30-batch3-guarded-first-run.xml`. No app ANR stack was available
in the shell-readable DropBox or `/data/anr`; the cause is not established from
this timeout alone. The chart's immutable height bounds were also cached to
remove repeated scans per vertex/frame; this corrects quadratic drawing work,
but is not claimed to resolve the focus timeout. All affected tasks are rerun.

The live emulator was then stopped normally and restarted without wiping its
data. Its new process reports **37.2.12**, whereas the earlier process used
37.1.11. SDK Manager installs the current official emulator package, so a refresh
can change it even when app dependencies are pinned. Runtime version mixing is
not established as the ANR cause, but the installer now refuses to refresh this
SDK while its emulator is running. This safety precondition was tested and
correctly rejected a live AVD before changing packages.

The fresh 37.2.12 process booted in about 215 seconds but reproduced a SystemUI
service ANR and a visible **Process system isn't responding** dialog before app
testing. Normal Wait did not produce a healthy preflight; process ANR flags
remained, so another app run was not started. The fresh-dialog XML is retained in
`qa/reports/api30-fresh-boot-system-dialog.xml`. The failing test AVD was stopped
normally, without wiping data. A second focused test was therefore **unrun**,
not passed or skipped.

Normal execution outside the restricted sandbox independently confirmed that
`/dev/kvm` is absent. The official acceleration check returns code 3:
`KVM requires a CPU that supports vmx or svm`. No available tool provisions a
different machine or exposes hardware virtualization. Stable device acceptance
remains unresolved; no watchdog, assertion or timeout was weakened. A KVM-capable
environment or accessible real Android test device is needed for reliable
acceptance testing; this does not establish the focus timeout's app-side cause.

Debug APK SHA-256:
`1aedb50da1cd3765569bdc72aeaa54548b9b98b4cbc793030280d200efea0916`
(after caching bounds; unit/build/lint and instrumentation compilation rerun).
The earlier pre-cache binary's SHA-256 was
`fb13a251f4696abdcf590c5c54ba67cb170543a3ab93adacc5dda5a35fa46a1e`.
All 35 frozen website hashes and all 26 packaged reference assets still match;
the 3 local license notices are included. All repository edits are Android-only.

## Still open

Selection is not yet synchronized to the map/GPU marker. Briefings/comparisons,
downhill flight, terrain overlay/lighting, lift status, the game catalog and all
five native engines remain open. State restoration, touch/large-font and sharing
need broader coverage. Updated API 28/API 36 suites and real-device performance
are not claimed passed. No release key, signed upgrade, website upload or
publication was performed.
