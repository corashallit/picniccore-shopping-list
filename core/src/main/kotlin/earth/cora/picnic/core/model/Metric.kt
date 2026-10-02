package earth.cora.picnic.core.model

/**
 * Detail lines are metric (§4.17, checklist 296): lb, oz, fl oz, qt, gal -> g, kg, mL, L. Cup, tbsp
 * and tsp survive only as a recipe amount after the "·". Owner: WP3 (MetricNormaliserTest).
 * PHASE-0 STUB: returns [quantity] unchanged until WP3 implements the conversion.
 */
fun normalizeMetric(quantity: String): String = quantity
