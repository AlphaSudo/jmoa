# V2 Final Performance Verdict

Status: `PERFORMANCE_GATES_PASSED`

The final public customers artifact passed both required release comparisons:

- B0 -> final V2: `SUBSTANTIAL_WIN`, 2/3 pairs, median PSS -8,956 KB.
- finalized V1 -> final V2: `SUBSTANTIAL_WIN`, 3/3 pairs, median PSS -4,812 KB.

The earlier direct run remains in the record as valid captured evidence, but it
is superseded for release acceptance because its fixed-order protocol did not
control host page-cache state. The candidate incurred roughly 60 MB of block IO
while its comparator incurred none, contaminating `memory.current` with an
unequal file-cache charge. The corrected protocol drops page cache before every
variant, records `memory.stat`, and balances pair order.

V2-C accepted every corrected run and both comparisons. V2-D attributes the
wins primarily to heap page-touch reduction rather than retained-object
reduction. This verdict does not establish a startup win or a universal Spring
Boot result.
