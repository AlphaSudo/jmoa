# V2 Performance Layer Isolation

Fresh one-pair screens reproduced both favorable and unfavorable outcomes. The
uncontrolled V1-to-V2 screen was the clearest diagnostic: PSS improved by
11,567 KB while `memory.current` regressed by 49,217,536 bytes because only V2
performed about 60 MB of cold block I/O.

After equalizing cache state, the three single screens were still mixed. This
identified heap page-touch and runtime variance as the interaction, rather than
an artifact or materialization defect. No screen was used as a release claim.

Decision: run balanced, cache-controlled three-pair confirmations for B0 versus
V2 and V1 versus V2, preserving all negative pairs.
