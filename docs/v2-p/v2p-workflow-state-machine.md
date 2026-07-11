# V2-P Workflow State Machine

`NOT_STARTED` advances only through the gate sequence. Recommendation or
preflight blocks produce their explicit blocked state. Materialization, smoke,
screen, and confirmation failures remain failures; they are never represented
as a completed workflow.

`CLAIM_ALLOWED` means that V2-C and V2-D artifacts make a narrowly scoped
claim eligible for human review. The coordinator never declares or publishes a
claim itself.
