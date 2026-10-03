package com.aiworkbench.community;

import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;

/** Deletes only synthetic rows owned by this test, never a shared account or schema. */
final class CommunityTestData {
    private CommunityTestData() {}
    static void remove(JdbcTemplate jdbc, UUID owner) {
        jdbc.update("DELETE FROM community_moderation_audit WHERE owner_id=? OR actor_id=?", owner, owner);
        jdbc.update("UPDATE community_posts SET status='DRAFT',current_revision_id=NULL,first_published_at=NULL WHERE owner_id=?", owner);
        jdbc.update("DELETE FROM community_revision_attachments WHERE owner_id=?", owner);
        jdbc.update("DELETE FROM community_draft_attachments WHERE owner_id=?", owner);
        jdbc.update("DELETE FROM community_attachments WHERE owner_id=?", owner);
        jdbc.update("DELETE FROM community_post_revisions WHERE owner_id=?", owner);
        jdbc.update("DELETE FROM community_post_drafts WHERE owner_id=?", owner);
        jdbc.update("DELETE FROM community_posts WHERE owner_id=?", owner);
        jdbc.update("DELETE FROM community_public_profiles WHERE owner_id=?", owner);
        jdbc.update("DELETE FROM work_records WHERE user_id=?", owner);
        jdbc.update("DELETE FROM focus_intervals WHERE user_id=?", owner);
        jdbc.update("DELETE FROM focus_sessions WHERE user_id=?", owner);
        jdbc.update("DELETE FROM task_events WHERE todo_id IN (SELECT id FROM todo_items WHERE user_id=?)", owner);
        jdbc.update("DELETE FROM todo_items WHERE user_id=?", owner);
        jdbc.update("DELETE FROM projects WHERE user_id=?", owner);
        jdbc.update("DELETE FROM user_accounts WHERE id=?", owner);
    }
}
