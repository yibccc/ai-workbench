package com.aiworkbench.mapper;

import com.aiworkbench.entity.community.AttachmentRow;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface AttachmentMapper {
    List<AttachmentRow> findAll(@Param("ownerId") UUID owner, @Param("postId") UUID post);
    List<AttachmentRow> lockAll(@Param("ownerId") UUID owner, @Param("postId") UUID post);
    Optional<AttachmentRow> findRequest(@Param("ownerId") UUID owner, @Param("postId") UUID post, @Param("requestId") UUID request);
    List<AttachmentRow> findDraft(@Param("ownerId") UUID owner, @Param("postId") UUID post);
    List<AttachmentRow> findRevision(@Param("ownerId") UUID owner, @Param("postId") UUID post, @Param("revisionId") UUID revision);
    List<AttachmentRow> findPublished(@Param("postId") UUID post, @Param("revisionId") UUID revision);
    Optional<AttachmentRow> findOwnedReadable(@Param("ownerId") UUID owner, @Param("postId") UUID post, @Param("id") UUID id);
    Optional<AttachmentRow> findPublishedReadable(@Param("postId") UUID post, @Param("id") UUID id);
    int insert(@Param("row") AttachmentRow row);
    int changeState(@Param("ownerId") UUID owner, @Param("postId") UUID post, @Param("id") UUID id,
            @Param("oldState") String oldState, @Param("state") String state, @Param("token") UUID token,
            @Param("code") String code, @Param("version") long version, @Param("now") Instant now);
    int insertDraft(@Param("ownerId") UUID owner, @Param("postId") UUID post, @Param("id") UUID id, @Param("position") int position);
    int deleteDraft(@Param("ownerId") UUID owner, @Param("postId") UUID post);
    int deleteDraftOne(@Param("ownerId") UUID owner, @Param("postId") UUID post, @Param("id") UUID id);
    int insertRevision(@Param("ownerId") UUID owner, @Param("postId") UUID post, @Param("revisionId") UUID revision,
            @Param("id") UUID id, @Param("position") int position);
    boolean referenced(@Param("ownerId") UUID owner, @Param("postId") UUID post, @Param("id") UUID id);
    int claimDelete(@Param("ownerId") UUID owner, @Param("postId") UUID post, @Param("id") UUID id,
            @Param("token") UUID token, @Param("deadline") Instant deadline, @Param("now") Instant now);
}
