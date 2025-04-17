-- Add timeseries columns to migration task table
ALTER TABLE migration_tasks
ADD COLUMN   timeseries  varchar(255);

ALTER TABLE migration_tasks
ADD COLUMN   database_name  varchar(255);

ALTER TABLE migration_tasks
DROP CONSTRAINT IF EXISTS UKne5ydyv5e0gl9v6vv588hxk6q;
