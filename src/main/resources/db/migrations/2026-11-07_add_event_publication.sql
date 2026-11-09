-- Spring Modulith event publication registry: events between modules are stored here until every
-- listener has handled them, and incomplete ones are retried on restart.
CREATE TABLE IF NOT EXISTS event_publication (
    id               UUID                     NOT NULL,
    listener_id      TEXT                     NOT NULL,
    event_type       TEXT                     NOT NULL,
    serialized_event TEXT                     NOT NULL,
    publication_date TIMESTAMP WITH TIME ZONE NOT NULL,
    completion_date  TIMESTAMP WITH TIME ZONE,
    PRIMARY KEY (id)
);
CREATE INDEX IF NOT EXISTS event_publication_serialized_event_hash_idx ON event_publication USING hash (serialized_event);
CREATE INDEX IF NOT EXISTS event_publication_by_completion_date_idx ON event_publication (completion_date);

-- If Hibernate created the table first (ddl-auto), its columns are varchar(255): too short for events
ALTER TABLE event_publication ALTER COLUMN serialized_event TYPE TEXT;
ALTER TABLE event_publication ALTER COLUMN listener_id TYPE TEXT;
ALTER TABLE event_publication ALTER COLUMN event_type TYPE TEXT;
