package app.osprey.media

/** A HEIC that cannot be turned, with the reason to tell the user. Thrown deep, answered once in [HeicTurn]. */
internal class HeicRefusal(val reason: String) : RuntimeException(reason)

internal fun refuseHeic(reason: String): Nothing = throw HeicRefusal(reason)
