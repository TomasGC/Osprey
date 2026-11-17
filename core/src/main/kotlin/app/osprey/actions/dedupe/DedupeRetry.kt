package app.osprey.actions.dedupe

import app.osprey.history.PlanFilter

/** Cuts a Dedupe plan down to the moves of the files a run failed on (#83). */
val DEDUPE_RETRY = PlanFilter<DedupePlan>(
    names = { plan -> plan.moves.map { it.sourceName } },
    only = { plan, names -> DedupePlan(plan.moves.filter { it.sourceName in names }) },
)
