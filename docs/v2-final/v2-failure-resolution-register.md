# V2 Failure Resolution Register

| Failure / Blocker | Original Result | Current Authoritative Result | Blocks v2.0.0? |
| --- | --- | --- | --- |
| V2-H hardened ASM reducer | Runtime screen regressed PSS/Private_Dirty. | Raw engine replaced this path; V2-H remains failed, V2-I confirms raw. | no |
| V2-Q application-class reducer | Artifact/semantic passed; 3-pair confirmation failed. | Runtime promotion blocked; low-ROI application policy added. | no |
| Doctor runtime blocked | Missing private stack/images/CDS path. | Stack recovered, CDS retrained, V2-K confirmed. | no |
| Stale CDS risk | Old archive could be reused incorrectly. | V2-N/V2-O block archive/artifact mismatch. | no |
| Generated-family mutation | Runtime relevance exists but no bounded safe transform. | V2-W closes generated mutation as discovery-only for V2. | no |
| Final public B0 -> V2 | Fresh direct run did not confirm. | P0 release blocker for performance headline. | yes |

