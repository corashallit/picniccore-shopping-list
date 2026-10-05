"""Which DSL sticker draws which catalog key (core/src/main/resources/icon_catalog.json), §18.3 step 4.

One drawable per catalog key: `sk_i_<catalog key>.xml`. The r9 stickers have short DSL keys and are mapped in CATALOG;
a new sticker simply uses its catalog key as its DSL key and needs no entry here. A DSL sticker listed in ORPHANS is
kept in the DSL (the mockup still shows it) but has no catalog key, so it is not emitted.
"""

# DSL key -> catalog key
CATALOG = {
    # fruit
    'strawberries': 'strawberry', 'blueberries': 'blueberries', 'banana': 'banana', 'lemon': 'lemon', 'apple': 'apple',
    'avocado': 'avocado', 'peachbasket': 'peach', 'grapes': 'grapes', 'oranges': 'orange', 'pear': 'pear',
    'cherries': 'cherries', 'watermelon': 'watermelon', 'limes': 'lime',
    # vegetables
    'carrots': 'carrot', 'broccoli': 'broccoli', 'tomatoes': 'tomato', 'onion': 'onion', 'garlic': 'garlic',
    'lettuce': 'lettuce', 'cucumber': 'cucumber', 'pepper': 'bell_pepper', 'potatoes': 'potato', 'corn': 'corn',
    'mushrooms': 'mushrooms', 'celery': 'celery', 'sweetpotato': 'sweet_potato',
    # bakery: the sourdough boule is exactly the catalog's bakery_generic ("a round rustic loaf ... scored cross",
    # aliases sourdough / artisan bread); the sandwich loaf becomes sliced_bread (§18.3 step 4)
    'baguette': 'baguette', 'croissant': 'croissant', 'sourdough': 'bakery_generic', 'loaf': 'sliced_bread',
    'buttertart': 'butter_tart', 'muffins': 'muffin', 'tortillas': 'tortillas', 'donuts': 'donut', 'pumpkinpie': 'pie',
    # deli
    'cheese': 'cheddar', 'curds': 'cheese_curds', 'ham': 'deli_meat', 'roastchicken': 'rotisserie_chicken',
    # meat: drumsticks keep chicken_drumstick; the chicken-thighs tray is the calibration set's "chicken tray" and
    # draws chicken_breast (pale pieces on a tray); peameal draws the catalog's own peameal_bacon key (§19.1, WP3)
    'drumstick': 'chicken_drumstick', 'chickentray': 'chicken_breast', 'peameal': 'peameal_bacon',
    'groundbeef': 'ground_beef', 'sausages': 'sausages', 'bacon': 'bacon', 'steak': 'steak',
    # seafood
    'salmon': 'salmon_fillet',
    # dairy
    'milkbag': 'milk_bag', 'eggs': 'eggs', 'butter': 'butter', 'yogurt': 'yogurt', 'cream': 'cream',
    'sourcream': 'sour_cream',
    # breakfast
    'maple': 'maple_syrup', 'coffee': 'coffee', 'tea': 'tea', 'jam': 'jam', 'cereal': 'cereal', 'honey': 'honey',
    'peanutbutter': 'peanut_butter', 'oatmeal': 'oatmeal', 'granola': 'granola', 'waffles': 'pancake_mix',
    # baking
    'flour': 'flour', 'sugar': 'sugar', 'bakingpowder': 'baking_soda', 'vanilla': 'vanilla',
    # condiments
    'salt': 'salt', 'ketchup': 'ketchup', 'mustard': 'mustard', 'oliveoil': 'olive_oil', 'soysauce': 'soy_sauce',
    'mayo': 'mayonnaise', 'hotsauce': 'hot_sauce',
    # pantry
    'tomatocan': 'canned_tomatoes', 'pasta': 'pasta', 'rice': 'rice', 'beans': 'canned_beans', 'soup': 'soup_can',
    'tuna': 'canned_tuna', 'spaghetti': 'spaghetti', 'pastasauce': 'pasta_sauce',
    # snacks
    'chips': 'potato_chips', 'crackers': 'crackers', 'cookies': 'cookies', 'popcorn': 'popcorn',
    'granolabar': 'granola_bar', 'chocolate': 'chocolate_bar',
    # beverages
    'lemonade': 'lemonade', 'sparkling': 'sparkling_water', 'orangejuice': 'orange_juice', 'water': 'bottled_water',
    # frozen
    'peas': 'frozen_vegetables', 'icecream': 'ice_cream', 'pizza': 'frozen_pizza', 'fries': 'french_fries',
    'icepops': 'popsicle',
    # household
    'dishsoap': 'dish_soap', 'toiletpaper': 'toilet_paper', 'laundry': 'laundry_detergent', 'papertowel': 'paper_towels',
    'garbagebags': 'garbage_bags', 'sponge': 'sponge', 'batteries': 'batteries',
    # health
    'toothpaste': 'toothpaste', 'vitamins': 'vitamins', 'bandages': 'bandages', 'tissues': 'tissues',
    'shampoo': 'shampoo', 'soap': 'hand_soap', 'sunscreen': 'sunscreen',
    # baby
    'diapers': 'diapers', 'babyfood': 'baby_food', 'wipes': 'baby_wipes',
    # pet
    'catfood': 'cat_food', 'litter': 'cat_litter', 'dogtreats': 'pet_treats',
}

# The gashapon capsule is the display-only sentinel IconRef("_capsule") (D5), not a catalog key.
SENTINELS = {'capsule': '_capsule'}

# DSL stickers with no catalog key (kept in the DSL, not emitted), with the reason.
ORPHANS = {
    'kibble': 'duplicates cat_food (§18.3 step 4): dropped',
    'lightbulb': 'no catalog key (§18.3 step 4): dropped',
    'pretzel': 'crackers already draws the "Crackers & pretzels" key; kept in the DSL as a part donor for snacks_generic',
}


def personality(catalog_value):
    """the hop personality (§8.3) the catalog gives a key (`"personality"` in icon_catalog.json, §19.1), else 'none'."""
    return catalog_value or 'none'


def catalog_key(dsl_key, catalog_keys):
    """the catalog key a DSL sticker draws, or None (an orphan)."""
    if dsl_key in CATALOG:
        return CATALOG[dsl_key]
    if dsl_key in SENTINELS:
        return SENTINELS[dsl_key]
    if dsl_key in catalog_keys:
        return dsl_key
    return None
