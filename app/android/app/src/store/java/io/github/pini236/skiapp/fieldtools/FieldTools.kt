package io.github.pini236.skiapp.fieldtools

import androidx.compose.runtime.Composable
import java.io.File

/**
 * The store build has nothing here: the field testers' tools live in src/preview only (docs/handoff/2026-10-31-tester-log-consent,
 * the architect's condition of 31.10.2026), and the store pipeline fails if a signed store build has a trace of them.
 */
object FieldTools {
    @Composable fun AboutRow() {}
    @Composable fun DiagRow() {}
    @Composable fun DayExtras(@Suppress("UNUSED_PARAMETER") file: File) {}
}
