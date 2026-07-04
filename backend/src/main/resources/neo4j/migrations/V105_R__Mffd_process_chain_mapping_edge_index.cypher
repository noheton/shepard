// Rollback for V105 — drop the has_successor.transitionKind index.
//
// Safe to run more than once; uses IF EXISTS.
// Edges keep their transitionKind property — only the index is dropped.

DROP INDEX rel_has_successor_transitionKind IF EXISTS;
