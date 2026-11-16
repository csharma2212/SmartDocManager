import sys
import re

sys.path.append('scratch')
from build_mappings import items

# Category codes and names
CATEGORIES = {
    'IDN': 'Identity',
    'EDU': 'Education',
    'MED': 'Medical',
    'FIN': 'Finance',
    'WRK': 'Work',
    'PRO': 'Property',
    'BIZ': 'Business',
    'LAW': 'Legal',
    'TPT': 'Transport',
    'TRV': 'Travel',
    'INS': 'Insurance',
    'UTL': 'Utilities',
    'AGR': 'Agriculture'
}

# Explicit mapping dictionary
explicit_cat = {
    # AGR
    'Kisan Credit Card Passbook': 'AGR',
    'Soil Health Card': 'AGR',
    'Crop Insurance Policy': 'AGR',
    'Agricultural Land Passbook': 'AGR',

    # UTL
    'Electricity Bill': 'UTL',
    'Water Bill': 'UTL',
    'Piped Gas Bill': 'UTL',
    'Liquefied Petroleum Gas Passbook': 'UTL',
    'Landline Telephone Bill': 'UTL',
    'Broadband Internet Bill': 'UTL',
    'Postpaid Mobile Bill': 'UTL',
    'Municipal Corporation Tax Receipt': 'UTL',

    # TRV
    'Visa Document': 'TRV',
    'DS-160 Form': 'TRV',
    'Schengen Visa Form': 'TRV',
    'Emigration Check Required Document': 'TRV',
    'Emigration Clearance Document': 'TRV',
    'Boarding Pass': 'TRV',
    'Flight Ticket': 'TRV',
    'Train Ticket': 'TRV',
    'Bus Ticket': 'TRV',
    'Hotel Booking Confirmation': 'TRV',
    'Customs Declaration Form': 'TRV',
    'International Driving Permit': 'TRV',
}

print(f"Explicit base set: {len(explicit_cat)}")
