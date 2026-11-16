package app.osprey.actions.reorg

import app.osprey.history.PlanFilter

/** Cuts a Reorg plan down to the items and duplicate pairs a run failed on (#83). */
val REORG_RETRY = PlanFilter<ReorgPlan>(
    names = { plan ->
        plan.items.map { it.label } + plan.duplicates.flatMap { listOf(it.input.label, it.library.label) }
    },
    only = { plan, names ->
        ReorgPlan(
            items = plan.items.filter { it.label in names },
            alreadyInOutput = emptyList(),
            duplicates = plan.duplicates.filter { it.input.label in names || it.library.label in names },
            undated = emptyList(),
            unreachable = emptyList(),
        )
    },
)

/**
 * What to do with the pairs of a retry: a side whose label is in [failed] goes to the Trash again, a side that did not
 * fail is kept. A pair with no failed side is not listed.
 */
fun ReorgPlan.retryChoices(failed: Set<String>): List<DuplicateChoice> =
    duplicates
        .filter { it.input.label in failed || it.library.label in failed }
        .map { DuplicateChoice(it, keepInput = it.input.label !in failed, keepLibrary = it.library.label !in failed) }
