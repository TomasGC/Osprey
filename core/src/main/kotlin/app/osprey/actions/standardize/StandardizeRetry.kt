package app.osprey.actions.standardize

import app.osprey.history.PlanFilter

/** Cuts a Standardize plan down to the renames of the files a run failed on (#83). */
val STANDARDIZE_RETRY = PlanFilter<StandardizePlan>(
    names = { plan -> plan.renames.map { it.sourceName } },
    only = { plan, names -> StandardizePlan(plan.renames.filter { it.sourceName in names }, emptyList()) },
)
