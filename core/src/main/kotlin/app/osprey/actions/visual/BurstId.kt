package app.osprey.actions.visual

private val burstIdPattern = Regex("""BURST(\d+)""")

/**
 * The shared id embedded in a burst-mode filename, if [name] contains the literal substring
 * `BURST` followed by digits anywhere — not tied to any one camera/phone maker's filename
 * format. Frames sharing a burst id are distinct shots, not duplicates, even though they're
 * visually near-identical, and must never be clustered together as a dup group — see
 * [VisualDupePlanner]. Returns null for a filename with no such substring. Ported from the
 * POC's `burst_id` unchanged — broadening this heuristic beyond the `BURST`-substring
 * convention is a separate, deliberate decision, not part of this port.
 */
fun burstId(name: String): String? = burstIdPattern.find(name)?.groupValues?.get(1)
