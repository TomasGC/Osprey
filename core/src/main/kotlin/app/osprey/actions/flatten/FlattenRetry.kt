package app.osprey.actions.flatten

import app.osprey.history.PlanFilter

/** Cuts a Flatten plan down to the moves of the files a run failed on (#83). */
val FLATTEN_RETRY = PlanFilter<FlattenPlan>(
    names = { plan -> plan.moves.map { it.sourcePath } },
    only = { plan, names -> FlattenPlan(plan.moves.filter { it.sourcePath in names }, plan.folders) },
)
