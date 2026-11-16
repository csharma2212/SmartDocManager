import sys
import re

sys.path.append('scratch')
from build_mappings import items

# Let's write the complete dictionary mapping of item -> (Category, Abbreviation)
# All 519 items explicitly!

mapping = {}

def add(item, cat, abbrev):
    assert len(cat) == 3, f"Bad cat: {cat}"
    assert len(abbrev) <= 24, f"Abbrev too long: {abbrev} ({len(abbrev)} chars) for {item}"
    assert re.match(r'^[A-Za-z0-9]+$', abbrev), f"Not alphanumeric: {abbrev}"
    filename = f"{cat}_{abbrev}_15Nov26.pdf"
    assert len(filename) <= 40, f"Filename too long: {filename} ({len(filename)} chars)"
    mapping[item] = (cat, abbrev, filename)

print("Helper defined.")
