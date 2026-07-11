# V2-P Claim Register Guard

`scripts/check-claim-register-consistency.ps1` rejects a workflow report that
declares a claim, marks a blocked workflow as claimable, lacks a V2-C
`CONFIRMED_WIN`, lacks V2-D attribution, or references claim-register text
that is absent.

It checks consistency; it does not automatically edit the claim register.
