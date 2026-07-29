# PetClinic Historical Comparator Closure

- Decision: **PETCLINIC_HISTORICAL_B0_CONTAMINATED**
- Performance run authorized: **False**
- Historical JMOA entries: **1**
- Disqualifying artifact differences: **2**

The historical artifact is not a clean no-JMOA B0. It contains JMOA output and
a semantically different application class. Running it against a newly built
baseline would compare different source/output universes and would not recover
the historical B0-to-V1 product effect.

The current six-order result remains authoritative for its own frozen current
artifacts and protocol. No historical-comparator performance run is authorized.
