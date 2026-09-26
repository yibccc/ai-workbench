package com.aiworkbench.mapper;

import com.aiworkbench.entity.project.ProjectRow;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface ProjectMapper {
    void insert(@Param("userId") UUID userId, @Param("id") UUID id, @Param("name") String name);
    Optional<ProjectRow> findById(@Param("userId") UUID userId, @Param("id") UUID id);
    List<ProjectRow> findAll(@Param("userId") UUID userId, @Param("includeArchived") boolean includeArchived);
    List<ProjectRow> findPage(@Param("userId") UUID userId, @Param("includeArchived") boolean includeArchived, @Param("q") String q);
    int rename(@Param("userId") UUID userId, @Param("id") UUID id, @Param("name") String name);
    int archive(@Param("userId") UUID userId, @Param("id") UUID id);
}
