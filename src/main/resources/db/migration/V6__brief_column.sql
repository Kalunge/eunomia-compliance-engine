-- Sprint 3 Chunk L: impact brief storage + new BRIEF_GENERATING status.
--
-- The brief markdown is stored inline on the regulation row rather
-- than in a separate table; there's exactly one brief per regulation
-- and "history" / versioning is explicitly out of scope. Regenerate
-- overwrites.
--
-- The status column gains BRIEF_GENERATING as an intermediate state
-- between MAPPED and BRIEF_READY so the UI can poll and show the
-- correct in-flight indicator.

ALTER TABLE regulations ADD COLUMN brief_markdown TEXT;

-- Drop the inline V2 check constraint and replace with the wider set.
-- The constraint name follows Postgres's default <table>_<column>_check
-- convention; the DO block makes the drop tolerant of any one-off
-- rename that might have happened.
DO $$
BEGIN
  IF EXISTS (
    SELECT 1 FROM pg_constraint
    WHERE conrelid = 'regulations'::regclass
      AND conname = 'regulations_status_check'
  ) THEN
    ALTER TABLE regulations DROP CONSTRAINT regulations_status_check;
  END IF;
END
$$;

ALTER TABLE regulations ADD CONSTRAINT regulations_status_check
    CHECK (status IN (
        'PARSING', 'EXTRACTED', 'MAPPED',
        'BRIEF_GENERATING', 'BRIEF_READY', 'FAILED'
    ));
