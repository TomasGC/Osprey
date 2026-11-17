package app.osprey.android

import app.osprey.history.PlanFilter

/**
 * What lets an action's runs be retried (#83): the [kind] its runs are recorded under, the [library] they ran on
 * (what is selected now), and the [filter] that cuts a plan down to the files a run failed on.
 */
class RetryWiring<Plan>(val kind: String, val library: () -> String, val filter: PlanFilter<Plan>)
