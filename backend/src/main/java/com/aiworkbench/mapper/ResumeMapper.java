package com.aiworkbench.mapper;

import com.aiworkbench.entity.resume.ResumeRows.*;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface ResumeMapper {
    Optional<UUID> lockOwner(@Param("owner") UUID owner);
    int ensureSlot(@Param("owner") UUID owner, @Param("now") Instant now);
    Optional<Current> current(@Param("owner") UUID owner);
    Optional<Current> lockCurrent(@Param("owner") UUID owner);
    Optional<ObjectRow> original(@Param("owner") UUID owner);
    List<ObjectRow> lockObjects(@Param("owner") UUID owner);
    Optional<ObjectRow> object(@Param("owner") UUID owner, @Param("id") UUID id);
    Optional<ReceiptRow> receipt(@Param("owner") UUID owner, @Param("operation") String operation, @Param("request") UUID request);
    int insertObject(@Param("row") ObjectRow row);
    int insertReceipt(@Param("row") ReceiptRow row);
    int saveCurrent(@Param("row") Current row, @Param("expected") long expected);
    int objectState(@Param("owner") UUID owner, @Param("id") UUID id, @Param("oldStatus") String oldStatus,
            @Param("token") UUID token, @Param("status") String status, @Param("code") String code, @Param("now") Instant now);
    int finishReceipt(@Param("owner") UUID owner, @Param("request") UUID request, @Param("state") String state,
            @Param("version") long version, @Param("code") String code);
    int completeIo(@Param("owner") UUID owner, @Param("id") UUID id, @Param("uploadToken") UUID uploadToken, @Param("now") Instant now);
    int claimDelete(@Param("owner") UUID owner, @Param("id") UUID id, @Param("token") UUID token, @Param("deadline") Instant deadline, @Param("now") Instant now);
    List<UUID> maintenanceOwners();
}
