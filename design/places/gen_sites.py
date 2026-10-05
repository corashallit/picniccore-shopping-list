"""Generates app/src/main/kotlin/earth/cora/picnic/platform/EcSites.kt from Environment Canada's city forecast site
list (site_list_en.csv here, a snapshot of https://dd.weather.gc.ca/today/citypage_weather/docs/site_list_en.csv).
Run from the repository root: python3 design/places/gen_sites.py"""
import csv
import os
import sys

HERE = os.path.dirname(os.path.abspath(__file__))
src = sys.argv[1] if len(sys.argv) > 1 else os.path.join(HERE, 'site_list_en.csv')
out = sys.argv[2] if len(sys.argv) > 2 else os.path.join(HERE, '..', '..', 'app', 'src', 'main', 'kotlin', 'earth', 'cora', 'picnic', 'platform', 'EcSites.kt')
rows = list(csv.reader(open(src, encoding='utf-8')))[2:]
MAJOR = {
    'Montréal', 'Québec', 'Gatineau', 'Sherbrooke', 'Trois-Rivières', 'Saguenay', 'Lévis',
    'Vancouver', 'Victoria', 'Kelowna', 'Abbotsford', 'Kamloops', 'Prince George',
    'Calgary', 'Edmonton', 'Red Deer', 'Lethbridge',
    'Winnipeg', 'Brandon', 'Regina', 'Saskatoon',
    'Halifax', 'Moncton', 'Saint John', 'Fredericton', 'Charlottetown', "St. John's",
    'Whitehorse', 'Yellowknife', 'Iqaluit',
}
# Forecast regions that are not a town (parks, islands, open water) or that duplicate a town in the list.
SKIP = ('Provincial Park', 'Algonquin Park', 'Toronto Island', 'Lake Superior', 'Sudbury (Greater)', 'Montreal River Harbour')


def deg(v):
    n = float(v[:-1])
    return -n if v[-1] in 'WS' else n


sites = []
for r in rows:
    if len(r) < 5 or not r[0].startswith('s'):
        continue
    code, name, prov, lat, lon = r[:5]
    if prov == 'ON':
        if any(s in name for s in SKIP):
            continue
    elif name not in MAJOR:
        continue
    sites.append((code, name, prov, deg(lat), deg(lon)))

sites.sort(key=lambda s: (s[2] != 'ON', s[2], s[1]))


def kstr(s):
    return '"' + s.replace('\\', '\\\\').replace('"', '\\"').replace('$', '\\$') + '"'


lines = [
    'package earth.cora.picnic.platform',
    '',
    '/*',
    ' * GENERATED from Environment Canada\'s city forecast site list',
    ' * (https://dd.weather.gc.ca/today/citypage_weather/docs/site_list_en.csv, fetched 2026-10-03): every Ontario town',
    ' * and city site (provincial parks, islands and open water left out) and the main cities of the other provinces and',
    ' * territories. Coordinates are the site\'s own (2 decimals), which is all a forecast request for that city names.',
    ' * Regenerate with design/places/gen_sites.py. Owner: WP8 (PlaceSourceTest).',
    ' */',
    '',
    '/** The bundled forecast cities (§0.4 Q6). */',
    'internal val EC_SITES: List<EcSite> = listOf(',
]
for code, name, prov, lat, lon in sites:
    lines.append(f'    EcSite({kstr(code)}, {kstr(name)}, {kstr(prov)}, {lat:.2f}, {lon:.2f}),')
lines.append(')')
lines.append('')
open(out, 'w', encoding='utf-8').write('\n'.join(lines))
print(len(sites), 'sites')
