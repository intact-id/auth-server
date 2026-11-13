-- Notifications can point at a console page, and rows with no company are the Intact staff inbox.
ALTER TABLE notifications ADD COLUMN IF NOT EXISTS link VARCHAR(255);

CREATE INDEX IF NOT EXISTS idx_notifications_company_status ON notifications (company_id, status);
