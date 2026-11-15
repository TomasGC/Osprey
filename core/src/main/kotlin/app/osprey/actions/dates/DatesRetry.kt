package app.osprey.actions.dates

import app.osprey.history.PlanFilter

/** Cuts a Dates plan down to the fixes of the files a run failed on (#83). */
val DATES_RETRY = PlanFilter<DatesPlan>(
    names = { plan -> plan.fixes.map { it.name } },
    only = { plan, names -> DatesPlan(plan.fixes.filter { it.name in names }, emptyList()) },
)
