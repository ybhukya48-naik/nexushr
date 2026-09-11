ALTER TABLE notifications
    ADD COLUMN notification_type VARCHAR(30) NOT NULL DEFAULT 'GENERAL';

ALTER TABLE notifications
    ADD COLUMN channel VARCHAR(20) NOT NULL DEFAULT 'IN_APP';

ALTER TABLE notifications
    ADD COLUMN delivery_status VARCHAR(20) NOT NULL DEFAULT 'SENT';

ALTER TABLE notifications
    ADD COLUMN recipient_email VARCHAR(255);

ALTER TABLE notifications
    ADD COLUMN recipient_phone VARCHAR(30);

ALTER TABLE notifications
    ADD COLUMN sent_at TIMESTAMP;

ALTER TABLE notifications
    ADD COLUMN failure_reason TEXT;
