package earth.cora.picnic.release

/*
 * The copy and IP rules the release scans enforce (WP10; docs/DESIGN_STICKER.md §16, §17, §20.18): what nothing the app
 * shows may say. One definition, used by the source scans (IpWordsTest, CopyRulesTest) and by the walk over every
 * screen (ScreenCopyTest). Each rule returns what it found in a text, so a failure names the word.
 */
object CopyRules {
    /** A rule: its name and the matches it finds in a text. */
    class Rule(val name: String, private val find: (String) -> List<String>) {
        fun matches(text: String): List<String> = find(text)
    }

    private fun words(vararg patterns: String, ignoreCase: Boolean = true): (String) -> List<String> {
        val options = if (ignoreCase) setOf(RegexOption.IGNORE_CASE) else emptySet()
        val rx = patterns.map { Regex("(?<![\\p{L}\\p{N}])(?:$it)(?![\\p{L}\\p{N}])", options) }
        return { text -> rx.flatMap { r -> r.findAll(text).map { it.value }.toList() } }
    }

    // ---- §17: IP ----

    /**
     * §17's "never" list and item 293's reference words: the franchises, their characters, phrases and look-alikes, and
     * the reference names (the 1990s sticker makers the direction was researched from).
     */
    val ipWords = Rule(
        "IP reference word (§17)",
        words(
            // The references (§17, item 293).
            "Sanrio", "Q-?Lia", "Bon ?bon( Drop)?", "Sun-?Star", "Bandai", "Amada", "Carddass", "San-X",
            // Sanrio and San-X characters (§17: "Sanrio-like characters (bow-wearing cat, pudding dog)").
            "Hello Kitty", "My Melody", "Kuromi", "Pompompurin", "Cinnamoroll", "Keroppi", "Pochacco", "Gudetama",
            "Badtz-?Maru", "Little Twin Stars", "Rilakkuma", "Sumikko( ?Gurashi)?",
            // Magical-girl franchises and their phrases (§17: "any franchise phrase").
            "Sailor", "Moon Prism", "Moon Crystal", "Moon Tiara", "Silver Millennium", "Usagi", "Tsukino", "Chibiusa",
            "Tuxedo Mask", "Pretty ?Cure", "Precure", "Cardcaptor", "Clow Card", "Doremi", "Minky Momo", "Creamy Mami",
            "Mew Mew",
            // §17's other names.
            "Strawberry Shortcake", "Care Bears?", "My Little Pony", "Lisa Frank", "Ghibli", "Totoro", "Holly Hobbie",
            "HBC", "Hudson'?s Bay", "point blanket", "trillium",
        ),
    )

    /**
     * §17: "real brand marks or labels", "the Foodland mark", "milk-bag brand art" (Cora, 3 October: brands on the input
     * side only). Grocery brands and stores she would meet in Ontario, as they are written (case-sensitive, so a common
     * word in a sentence is not a brand). Catalog aliases, parser and eval inputs and prompt examples may name them.
     */
    val brands = Rule(
        "real brand or store (§17)",
        words(
            // Stores.
            "Foodland", "Loblaws?", "No Frills", "Sobeys", "FreshCo", "Zehrs", "Fortinos", "Valu-?mart", "Your Independent Grocer",
            "Real Canadian Superstore", "Walmart", "Costco", "Farm Boy", "Longo'?s", "Whole Foods", "T&T", "Food Basics",
            "Giant Tiger", "Shoppers Drug Mart", "Rexall", "Safeway", "IGA", "Provigo", "Save-On-Foods", "Metro Inc", "Kroger",
            "Trader Joe'?s", "Aldi", "Lidl", "Tim Hortons", "Timbits", "Starbucks", "Second Cup",
            // House brands.
            "President'?s Choice", "Great Value", "Kirkland Signature", "Life Brand", "Compliments",
            // Grocery brands.
            "Kraft", "KD", "Kraft Dinner", "Oatly", "Heinz", "Nutella", "Cheerios", "Kellogg'?s?", "Nestl[eé]", "Nescaf[eé]",
            "Coca-Cola", "Coke", "Pepsi", "Natrel", "Lactantia", "Neilson", "Beatrice", "Gay Lea", "Dempster'?s", "Wonder Bread",
            "Christie", "Oreos?", "Ritz", "Kleenex", "Cottonelle", "Charmin", "Royale", "Purina", "Whiskas", "Meow Mix", "Iams",
            "Colgate", "Sensodyne", "Crest", "Palmolive", "Sunlight", "Cascade", "Dawn", "Tide", "Ziploc", "Lysol", "Clorox",
            "Javex", "H[aä]agen-Dazs", "Ben & Jerry'?s", "Breyers", "Chapman'?s", "Green Giant", "McCain", "Eggo",
            "Hellmann'?s", "Philadelphia", "Tostitos", "Doritos", "Lay'?s", "Pringles", "Kit ?Kat", "Coffee Crisp", "Hershey'?s?",
            "Lindt", "Ferrero", "Skittles", "Maynards", "Nabob", "Folgers", "Maxwell House", "Red Rose", "Tetley", "Lipton",
            "Nespresso", "Keurig", "Tassimo", "Tropicana", "Minute Maid", "Gatorade", "Perrier", "San Pellegrino", "Canada Dry",
            "Schweppes", "Activia", "Danone", "Oikos", "Libert[eé]", "Yoplait", "Becel", "Crisco", "Robin Hood", "Five Roses",
            "Redpath", "Lantic", "Clover Leaf", "Catelli", "Barilla", "Ragu", "Prego", "Campbell'?s", "Uncle Ben'?s",
            "Ben'?s Original", "French'?s", "Kikkoman", "Club House", "Old El Paso", "Quaker", "Jif", "Skippy", "Smucker'?s",
            "E\\.? ?D\\.? Smith", "Aunt Jemima", "Pillsbury", "Betty Crocker", "Duncan Hines", "Jell-?O", "Lunchables",
            "Swiffer", "Huggies", "Pampers", "Gerber", "Similac", "Enfamil", "Tylenol", "Advil", "Band-?Aid", "Q-?tips",
            "Vaseline", "Pantene", "Gillette", "Listerine", "Oral-?B", "Rice Krispies", "Froot Loops", "Shreddies",
            "Special K", "Mini-?Wheats", "Pop-?Tarts", "Knorr", "OXO", "Bisto", "Lea & Perrins", "Tabasco", "Frank'?s RedHot",
            "Bick'?s", "Vachon", "Jos Louis", "Caramilk", "Snickers", "Twix", "M&M'?s", "Haribo", "Fleischmann'?s",
            "Arm & Hammer", "Dr\\.? Oetker", "Magic Baking Powder", "Silk", "Astro", "Danactive",
            ignoreCase = false,
        ),
    )

    // ---- §16, item 294 ----

    /** "No copy mentions a proxy" (§16; the app talks to OpenAI with her own key, D3, A1). */
    val proxy = Rule("proxy (§16)", words("prox(y|ies)"))

    /** "No copy mentions … model names" (§16, A1): OpenAI's model families and other assistants. */
    val modelNames = Rule(
        "model name (§16)",
        words(
            "gpt(-[\\w.]+)*", "chatgpt", "dall-?e( ?\\d)?", "whisper(-\\d)?", "davinci", "o[134](-mini|-pro)?", "tts-1(-hd)?",
            "text-embedding[\\w-]*", "claude", "gemini", "llama", "mistral", "sora", "codex",
            "[\\w.-]+-(flare|terra|luna|transcribe|mini|nano|turbo)",
        ),
    )

    /** "No copy mentions … prices" (§16, D18): no money, no sales. */
    val prices = Rule(
        "price (§16, D18)",
        { text ->
            val money = Regex("""\$\s?\d|\d\s?\$|¢|\bCAD\b|\bUSD\b""").findAll(text).map { it.value }.toList()
            money + words("prices?", "priced", "pricing", "on sale", "sale", "coupons?", "dollars?", "cents", "cheap(er|est)?", "deals?")(text)
        },
    )

    /**
     * "None uses an emoji as an icon" (§16, §17 "emoji standing in for icons"): no character drawn as a colour emoji. A
     * codepoint with emoji presentation, an emoji variation selector (U+FE0F), a keycap, a skin-tone modifier or a
     * regional-indicator flag. The kit's own symbols (♡ ★ ☆ ✦ ♪) are text-presentation characters drawn as vector inline
     * content (§16) and are fine.
     */
    val emoji = Rule(
        "emoji as an icon (§16, §17)",
        { text ->
            val found = ArrayList<String>()
            var i = 0
            while (i < text.length) {
                val cp = text.codePointAt(i)
                if (isEmoji(cp)) found += String(Character.toChars(cp))
                i += Character.charCount(cp)
            }
            found
        },
    )

    /**
     * Unicode's Emoji_Presentation characters of the BMP (emoji-data.txt, Unicode 15.1): drawn as colour emoji by default.
     * Everything from U+1F000 on (the pictographs, flags and skin tones) counts as emoji too.
     */
    private val bmpEmojiPresentation: List<IntRange> = listOf(
        0x231A..0x231B, 0x23E9..0x23EC, 0x23F0..0x23F0, 0x23F3..0x23F3, 0x25FD..0x25FE, 0x2614..0x2615, 0x2648..0x2653,
        0x267F..0x267F, 0x2693..0x2693, 0x26A1..0x26A1, 0x26AA..0x26AB, 0x26BD..0x26BE, 0x26C4..0x26C5, 0x26CE..0x26CE,
        0x26D4..0x26D4, 0x26EA..0x26EA, 0x26F2..0x26F3, 0x26F5..0x26F5, 0x26FA..0x26FA, 0x26FD..0x26FD, 0x2705..0x2705,
        0x270A..0x270B, 0x2728..0x2728, 0x274C..0x274C, 0x274E..0x274E, 0x2753..0x2755, 0x2757..0x2757, 0x2795..0x2797,
        0x27B0..0x27B0, 0x27BF..0x27BF, 0x2B1B..0x2B1C, 0x2B50..0x2B50, 0x2B55..0x2B55,
    )

    /** A codepoint drawn as a colour emoji: emoji presentation, the emoji variation selector, a keycap, or U+1F000 on. */
    fun isEmoji(cp: Int): Boolean = cp >= 0x1F000 || cp == 0xFE0F || cp == 0x20E3 || bmpEmojiPresentation.any { cp in it }

    // ---- §16, item 295 ----

    /** Canadian spelling (§16: "colour, favourite"): the American spellings of the words the app could use. */
    val americanSpelling = Rule(
        "American spelling (§16: Canadian)",
        words(
            "colors?", "colored", "coloring", "colorful", "favorites?", "favorited", "flavors?", "flavored", "honors?",
            "neighbors?", "labors?", "behaviors?", "humors?", "rumors?", "savory", "centers?", "centered", "theaters?",
            "liters?", "milliliters?", "millimeters?", "centimeters?", "kilometers?", "grays?", "catalogs?",
            "canceled", "canceling", "labeled", "labeling", "traveled", "traveling", "modeled", "fueled", "jewelry",
            "defense", "offense", "licenses?",
        ),
    )

    /** Metric only (§16): no imperial units in what the app says. */
    val imperial = Rule(
        "imperial unit (§16: metric only)",
        { text ->
            words(
                "lbs?", "pounds?", "oz", "ounces?", "fl\\.? ?oz", "pints?", "quarts?", "qts?", "gallons?", "inch(es)?",
                "feet", "ft", "yards?", "yd", "miles?", "°F", "Fahrenheit",
            )(text) + Regex("""\d\s?°F|\d\s?(?:"|″)(?![\p{L}])""").findAll(text).map { it.value }.toList()
        },
    )

    /** A cooking measure with its amount: "2 cups", "½ tsp", "a pinch". */
    private val cookingAmount =
        Regex("""(?<![\p{L}\p{N}])(?:\d+(?:[.,/]\d+)?|[½¼¾⅓⅔⅛]|a|one|two|three|half a)\s*(?:cups?|tbsps?|tsps?|tablespoons?|teaspoons?|pinch(?:es)?)(?![\p{L}\p{N}])""", RegexOption.IGNORE_CASE)

    /**
     * Cooking measures (§4.17, §16): a recipe row's amount may keep cup, tbsp, tsp or pinch, and nothing else may. The
     * amount ends the row's detail, after its "·" ("in your pantry · ½ tsp"; TalkBack reads the row with commas, "Salt,
     * in your pantry, ½ teaspoon"). This rule finds every other measure.
     */
    val cooking = Rule("cooking measure outside a recipe amount (§4.17)") { text ->
        cookingAmount.findAll(text).filter { m ->
            val before = text.substring(0, m.range.first).trimEnd()
            val recipeAmount = (before.endsWith('·') || before.endsWith(',')) && text.substring(m.range.last + 1).isBlank()
            !recipeAmount
        }.map { it.value }.toList()
    }

    /** Whether [text] is a recipe amount (§4.17): its measures all end a row's detail after its separator. */
    fun cookingAllowed(text: String): Boolean = cooking.matches(text).isEmpty()

    /** The IP rules (item 293). */
    val ip: List<Rule> = listOf(ipWords, brands)

    /** Item 294's rules. */
    val copy: List<Rule> = listOf(proxy, modelNames, prices, emoji)

    /** Item 295's rules (cooking measures apart: see [cookingAllowed]). */
    val canadianMetric: List<Rule> = listOf(americanSpelling, imperial)

    /** A format specifier ("%1\$d", "%s"): a slot the app fills, not words. */
    private val formatSpecifier = Regex("""%(\d+\$)?[-#+ 0,(]*\d*(\.\d+)?[a-zA-Z%]""")

    /**
     * The proper names of the third-party licences the About page must show as they are (§2.7: the fonts' OFL texts ship
     * on the licences page): "SIL Open Font License" is the licence's name, not the app's spelling.
     */
    private val licenceNames = Regex("""(SIL )?Open Font License|Apache License|MIT License""")

    /** [text] as the rules read it: format slots out, licence names out. */
    fun prepared(text: String): String = text.replace(formatSpecifier, "{}").replace(licenceNames, "{}")

    /** Every rule [rules] breaks in [found], as "rule: word in place". */
    fun offences(found: Iterable<Found>, rules: List<Rule>): List<String> =
        found.flatMap { f -> rules.flatMap { r -> r.matches(prepared(f.text)).map { w -> "${r.name}: '$w' in $f" } } }

    /** The cooking measures in [found] that are not a recipe amount. */
    fun cookingOffences(found: Iterable<Found>): List<String> = offences(found, listOf(cooking))
}
