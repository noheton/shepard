// V83 ROLLBACK — drops the :ImportPlan(commitId) index added by V83.
//
// The manifestJson property requires no rollback action — it is
// simply left unpopulated on existing plan nodes (which is the same
// state as before V83 was applied, since the Java layer didn't set it).

DROP INDEX import_plan_commit_id IF EXISTS;
