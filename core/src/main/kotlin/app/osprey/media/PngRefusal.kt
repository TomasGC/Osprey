package app.osprey.media

/** A PNG that cannot be turned, with the reason to tell the user. Thrown deep, answered once in [PngTurn]. */
internal class PngRefusal(val reason: String) : RuntimeException(reason)

internal fun refuse(reason: String): Nothing = throw PngRefusal(reason)
