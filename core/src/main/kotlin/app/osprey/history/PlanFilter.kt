package app.osprey.history

/**
 * How to cut an action's plan down to the files a run failed on (#83): [names] are the names the plan's items record
 * when they are applied, and [only] keeps the items whose name is in the set, in plan order.
 */
class PlanFilter<Plan>(val names: (Plan) -> List<String>, val only: (Plan, Set<String>) -> Plan)
