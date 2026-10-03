package com.aiworkbench.mapper;

import com.aiworkbench.entity.community.CommunityRows.*;
import com.aiworkbench.enums.CommunityPostStatus;
import com.aiworkbench.enums.CommunityPostType;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface CommunityPostMapper {
    void insertPost(@Param("row") Post row);
    void insertDraft(@Param("row") Draft row);
    int updateDraft(@Param("row") Draft row);
    Optional<Post> findOwned(@Param("ownerId") UUID ownerId, @Param("postId") UUID postId);
    Optional<Post> lockOwned(@Param("ownerId") UUID ownerId, @Param("postId") UUID postId);
    Optional<Post> lockForModeration(@Param("postId") UUID postId);
    Optional<Draft> findDraft(@Param("ownerId") UUID ownerId, @Param("postId") UUID postId);
    Optional<Revision> findRevision(@Param("ownerId") UUID ownerId, @Param("postId") UUID postId,
            @Param("revisionId") UUID revisionId);
    Optional<Revision> findPublishResult(@Param("ownerId") UUID ownerId, @Param("postId") UUID postId,
            @Param("requestId") UUID requestId);
    int nextRevisionNo(@Param("ownerId") UUID ownerId, @Param("postId") UUID postId);
    void insertRevision(@Param("row") Revision row);
    int updateAggregate(@Param("ownerId") UUID ownerId, @Param("postId") UUID postId,
            @Param("version") long version, @Param("status") CommunityPostStatus status,
            @Param("revisionId") UUID revisionId, @Param("firstPublishedAt") Instant firstPublishedAt,
            @Param("updatedAt") Instant updatedAt);
    List<OwnerCard> findOwnedPage(@Param("ownerId") UUID ownerId,
            @Param("status") CommunityPostStatus status, @Param("type") CommunityPostType type);
    List<PublicPost> findPublishedPage(@Param("type") CommunityPostType type, @Param("authorId") UUID authorId);
    Optional<PublicPost> findPublished(@Param("postId") UUID postId);
    Optional<Moderation> findModeration(@Param("ownerId") UUID ownerId, @Param("postId") UUID postId);
    void insertModeration(@Param("id") UUID id, @Param("postId") UUID postId, @Param("ownerId") UUID ownerId,
            @Param("actorId") UUID actorId, @Param("revisionId") UUID revisionId,
            @Param("reason") String reason, @Param("occurredAt") Instant occurredAt);
}
