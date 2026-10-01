package earth.cora.picnic.core.model

import java.text.Normalizer

/*
 * Name normalization and slugs. CONTRACT: aisle ids, correction keys and the bespoke-icon
 * cache key are derived from these functions and persisted, so their output for a given
 * input must never change once shipped (add a migration if it must).
 */

private val COMBINING_MARKS = Regex("\\p{Mn}+")
private val NON_ALNUM = Regex("[^a-z0-9]+")

/** Lowercase, fold accents ("Crème" -> "creme", "œ" -> "oe"), keep only [a-z0-9] runs separated by single spaces. */
fun foldText(text: String): String {
    val lowered = text.lowercase()
        .replace("œ", "oe").replace("æ", "ae").replace("ß", "ss")
        .replace('’', '\'')
    val stripped = Normalizer.normalize(lowered, Normalizer.Form.NFKD).replace(COMBINING_MARKS, "")
    return stripped.replace("'", "").replace(NON_ALNUM, " ").trim()
}

/**
 * Stable slug for aisle ids: foldText, words joined by '-'.
 * "Aisle 2 - Baking & spices" -> "aisle-2-baking-spices"; "Fruits et légumes" -> "fruits-et-legumes".
 * Returns "" for input without letters or digits (Aisles.parse then assigns "aisle-<position>").
 */
fun slugify(text: String): String = foldText(text).replace(' ', '-')

/**
 * Key for dedupe, learned corrections and the bespoke-icon cache: foldText plus a conservative
 * singularization of the LAST word only ("2 Lemons" is not stripped of the "2" here; callers pass names).
 * Rules for the last word, applied in order, only when the word is longer than 3 characters:
 *   "...ies" -> "...y" (berries -> berry), "...oes" -> "...o" (tomatoes -> tomato),
 *   "...ss"/"...us"/"...is" unchanged (glass, hummus, ...), otherwise a trailing "s" is dropped (lemons -> lemon).
 * Examples: "Lemons" -> "lemon", "Crème fraîche" -> "creme fraiche", "Hummus" -> "hummus", "Eggs" -> "egg".
 */
fun normalizeName(name: String): String {
    val folded = foldText(name)
    if (folded.isEmpty()) return folded
    val words = folded.split(' ')
    val last = words.last()
    val singular = when {
        last.length <= 3 -> last
        last.endsWith("ies") -> last.dropLast(3) + "y"
        last.endsWith("oes") -> last.dropLast(2)
        last.endsWith("ss") || last.endsWith("us") || last.endsWith("is") -> last
        last.endsWith("s") -> last.dropLast(1)
        else -> last
    }
    return (words.dropLast(1) + singular).joinToString(" ")
}
