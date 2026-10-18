-- Optional additive migration for MySQL 8. This project has no automatic migration runner.
-- Review SHOW INDEX first and apply only to an approved environment.
-- No row is modified; large tables may require a planned online DDL window.
CREATE INDEX idx_chatbot_interactions_conversation_id_id
    ON chatbot_interactions (conversation_id, id);
CREATE INDEX idx_chatbot_topics_conversation_updated_id
    ON chatbot_topics (conversation_id, updated_at, id);
CREATE INDEX idx_chatbot_interactions_comparison_history
    ON chatbot_interactions (conversation_id, intent, processing_status, id);

-- Rollback of this migration only (execute separately if needed):
-- DROP INDEX idx_chatbot_interactions_conversation_id_id ON chatbot_interactions;
-- DROP INDEX idx_chatbot_topics_conversation_updated_id ON chatbot_topics;
-- DROP INDEX idx_chatbot_interactions_comparison_history ON chatbot_interactions;
