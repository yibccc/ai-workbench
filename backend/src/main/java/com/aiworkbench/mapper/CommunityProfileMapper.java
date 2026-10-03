package com.aiworkbench.mapper;

import com.aiworkbench.entity.community.CommunityRows.Author;
import com.aiworkbench.entity.community.CommunityRows.Profile;
import java.util.Optional;
import java.util.UUID;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface CommunityProfileMapper {
    Optional<Author> findAuthor(@Param("ownerId") UUID ownerId);
    Optional<Profile> findProfile(@Param("ownerId") UUID ownerId);
    int insertProfile(@Param("ownerId") UUID ownerId, @Param("nickname") String nickname, @Param("bio") String bio);
    int updateProfile(@Param("ownerId") UUID ownerId, @Param("nickname") String nickname,
            @Param("bio") String bio, @Param("version") long version);
}
