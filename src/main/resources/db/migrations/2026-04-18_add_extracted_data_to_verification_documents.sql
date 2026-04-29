ALTER TABLE verification_documents
    ADD COLUMN IF NOT EXISTS extracted_data jsonb;
