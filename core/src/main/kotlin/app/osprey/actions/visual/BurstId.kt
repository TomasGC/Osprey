package app.osprey.actions.visual

// Any case, since another tool may have lowercased the name. No letter before, so `sunburst2` is not a burst; nor
// across a hyphen (`star-burst1`), and a mark counts as a letter, for an accent stored apart from it (#141).
private val burstIdPattern = Regex("""(?<![\p{L}\p{M}]-?)BURST(\d+)""", RegexOption.IGNORE_CASE)

/**
 * The shared id embedded in a burst-mode filename: the digits after `BURST`, in any case and not at the end of a
 * word, anywhere in [name] -- not tied to any one camera or phone maker's filename format. Frames sharing a burst id
 * are distinct shots, not duplicates, even though they are visually near-identical, and must never be clustered
 * together as a dup group -- see [VisualDupePlanner]. Returns null for a name with no such id.
 */
fun burstId(name: String): String? = burstIdPattern.find(name)?.let { it.groupValues[1] }
