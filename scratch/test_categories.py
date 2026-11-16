import sys
import re

sys.path.append('scratch')
from build_mappings import items

# 13 Categories mapping
# Let's write a python script that will classify each of the 519 items and generate an abbrev <= 24 chars.

raw_items = items

# We can categorize each item into one of the 13 categories:
# IDN, EDU, MED, FIN, WRK, PRO, BIZ, LAW, TPT, TRV, INS, UTL, AGR

print(f"Total items: {len(raw_items)}")
