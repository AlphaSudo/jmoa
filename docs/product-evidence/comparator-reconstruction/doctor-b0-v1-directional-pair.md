# Doctor B0/V1 Directional Pair

- Decision: **DOCTOR_HISTORICAL_V1_DIRECTION_NOT_REPRODUCED_IN_SINGLE_ORDER**
- Evidence status: **valid diagnostic**
- B0 PSS: **305850 KB**
- V1 PSS: **308511 KB**
- V1 - B0 PSS: **2661 KB**
- V1 - B0 Private_Dirty: **2580 KB**
- V1 - B0 memory.current: **2789376 bytes**
- Loaded-class delta: **-116**
- NMT committed delta: **-366 KB**
- Attribution: **NMT_PARTIAL_ANONYMOUS_RW_OUTSIDE_HEAP**

Historical reference:

- paired-delta median: **-2,036 KB**
- independent-median delta: **-2,728 KB**
- old published **-6,048 KB**: **invalid historical median calculation**

The exact historical B0 and V1 application artifacts were compared under the same reconstructed
base-CDS runtime. V1 reduced loaded classes but increased anonymous PSS and Private_Dirty. The old
negative direction did not return. This diagnostic is not a performance claim, and it does not
authorize the six-order Doctor campaign. It is superseded by the completed two-order
`V1_RUNTIME_COST` diagnostic and retained as the first-order record.
