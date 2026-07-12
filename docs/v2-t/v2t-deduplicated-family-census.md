# V2-T Deduplicated Family Census

V2-T uses exclusive primary-family ownership: every generated-like class is
counted once under the classifier's primary family. Synthetic, bridge, and
lambda indicators are reported as overlapping signals, not additive class
totals.

| Service | Unique generated-like classes | Unique classfile bytes | Overlapping signal classes |
| --- | ---: | ---: | ---: |
| PetClinic customers | 12,152 | 81,625,377 | 8,805 |
| Doctor corrected D2R | 14,469 | 96,525,651 | 10,144 |

Customers primary-family census: lambda/metafactory 3,446 classes / 39,086,800
bytes; Spring Data 290 / 1,379,849; ByteBuddy 21 / 222,724; Hibernate proxy 2 /
4,136; synthetic/bridge 8,393 / 40,931,868.

Doctor D2R primary-family census: lambda/metafactory 4,001 / 44,126,922; Spring
CGLIB 12 / 90,084; Spring AOT BeanDefinitions 322 / 1,057,731; Spring AOT
registration 2 / 122,767; Spring Data 293 / 1,387,124; ByteBuddy 21 / 222,724;
Hibernate proxy 2 / 4,136; synthetic/bridge 9,816 / 49,514,163.

These are static packaged surfaces, not loaded-class counts and not runtime
memory attribution.
