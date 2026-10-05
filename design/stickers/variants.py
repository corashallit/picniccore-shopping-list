"""Sticker variants shared by the mockup and the Android emitter.

FACES: the kawaii face (deco.face) for the album, achievements and empty states only (§3.2), never on a list
sticker: DSL key -> (centre x, centre y, family, eye spread). The emitter writes them as `sk_f_<catalog key>.xml`
overlays. HOLO: the part the mockup's holo demo clips to (the app clips to the emitted `dominant` group).
"""
HOLO = {'apple': 2, 'maple': 1, 'croissant': 4}
FACES = {'apple': (24, 29.4, 'strawberry', 4.4), 'milkbag': (25, 33, 'ramune', 3.6), 'sourdough': (24, 33.6, 'cocoa', 4.6)}
