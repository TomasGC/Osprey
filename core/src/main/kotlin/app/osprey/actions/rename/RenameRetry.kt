package app.osprey.actions.rename

import app.osprey.history.PlanFilter

/** Cuts a Rename plan down to the renames of the files a run failed on (#83). */
val RENAME_RETRY = PlanFilter<RenamePlan>(
    names = { plan -> plan.renames.map { it.sourceName } },
    only = { plan, names -> RenamePlan(plan.renames.filter { it.sourceName in names }, emptyList()) },
)
