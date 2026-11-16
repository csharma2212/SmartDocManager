import sys
import re

sys.path.append('scratch')
from build_mappings import items

# Let's write the complete, handcrafted, rigorous classification for all 519 items!

# 13 Categories:
# Identity (IDN)
# Education (EDU)
# Medical (MED)
# Finance (FIN)
# Work (WRK)
# Property (PRO)
# Business (BIZ)
# Legal (LAW)
# Transport (TPT)
# Travel (TRV)
# Insurance (INS)
# Utilities (UTL)
# Agriculture (AGR)

doc_data = []

# Let's inspect all items and define explicit (orig, cat, abbrev) for each
