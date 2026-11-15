package app.osprey.actions.reorg

/** A move that copied a file but could not prove the copy is the same file. */
internal class VerificationException(message: String) : IllegalStateException(message)
