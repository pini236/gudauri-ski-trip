# Native app relocation — 2026-10-01

The user moved this ChatGPT app on `main` to `chatgpt/android-native/`.
The separate apps planned under `app/` are out of scope.

## Preservation and synchronization

- Previous checkout: `d0a2e88dbc7802a100fe997c8c3b350913ad80a1`.
- Read-only fetch observed `main` at `49fe97bdda4bc2261b7e09406c45d1fb5af31465`.
- Local tracked and untracked parity work was saved in a retained Git stash,
  `8c679b95c86105e65b31dfc084c3d523e7ba869a`, before a fast-forward only sync.
- Restoring the stash followed tracked-file renames. Two conflicts were resolved
  by preserving the upstream data path and all local asset/README additions.
- Untracked source, tools, docs and historical QA evidence were relocated after
  checking that no destination file overlapped. Old generated outputs/caches
  were preserved outside the checkout, not deleted.
- Preservation verification found 73 relocated files byte-identical to the
  retained stash. Only enumerated path/documentation corrections differ.

## Adjustments

- Unit-test data: `../../site/data` relative to this module.
- Packaged data, panoramas, audio and game thumbnails: `../../../site/…` relative
  to `app/`; licenses remain `../licenses`.
- SDK-independent domain runner, source links and runnable documentation now
  resolve from the new module. The external installer and saved start workflow
  use `/workspace/gudauri-ski-trip/chatgpt/android-native`.
- No website, shared-data, other-app or root-doc local edits. Upstream website
  changes are the user's existing changes, not part of this app patch.

## Validation

The first native check ran all 37 unit tests, with 36 passing and one failing:
`MeetingTest` still resolved the inventory from the old root directory. Its
`FileNotFoundException` report is retained in
`qa/reports/relocation-first-run-inventory-path.xml`. The path was corrected
without changing assertions, fixtures or timeouts. The rerun completed with
**37 passed, zero failures/errors/skips** (2026-10-01 18:14:49–50 UTC). All five
suite XML files are retained as `qa/reports/relocation-final-*.xml`.

`:app:assembleDebug`, `:app:lintDebug` and `:app:assembleDebugAndroidTest` also
passed in that run (Gradle: 54 seconds; 23 tasks executed, 58 up-to-date).
All four reference JSON files are unchanged from the original parity baseline.
All **29** generated assets/licenses match the current reference, including the
upstream-updated game thumbnail, and the same bytes were verified inside the
final APK. Debug signature verification and 16 KiB page-alignment checks passed
using explicit SDK build-tools paths; those tools are not on the helper's PATH.

APK: `app/build/outputs/apk/debug/app-debug.apk`.
SHA-256: `22e4a95ce0e76d4753f1d8c425409853cbc3c996371435e73f999cda6dc3ede1`.
The app identifier, version and signing approach are unchanged; this is not a
side-by-side testing variant or a verified upgrade of the user's installed app.

The external installer differs from its previously executed version only in
the module `cd` path; shell syntax and the affected Gradle workflow were checked.
The whole installer was not rerun for this migration. Complete `install_script`
and `start_skill` fields were saved and read back at draft revision 10; network,
runtime requirements, repository bindings and secret metadata were preserved.
This is a saved draft, not publication or a test in a new machine.
No emulator or phone test was started for this path-only migration.

The retained 100-case browser result and original inventory are historical for
`d0a2e88`, not validation of `49fe97b`. The newer reference adds about/settings
and visual fixes; delta mapping is explicitly pending in the parity plan.
No public release, signed upgrade or installation on the user's phone occurred.
