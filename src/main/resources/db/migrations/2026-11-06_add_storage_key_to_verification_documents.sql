ALTER TABLE verification_documents
    ADD COLUMN IF NOT EXISTS storage_key VARCHAR(512);
