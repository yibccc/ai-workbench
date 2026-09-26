package com.aiworkbench.mapper;

import com.aiworkbench.entity.account.AccountRow;
import java.util.List;
import java.util.UUID;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface AccountMapper {
    AccountRow findById(@Param("id") UUID id);
    AccountRow findByUsername(@Param("username") String username);
    List<AccountRow> list();
    long count();
    long enabledAdminCount();
    void lockAdministration();
    int insert(@Param("account") AccountRow account);
    int updatePassword(@Param("id") UUID id, @Param("passwordHash") String passwordHash);
    int updatePasswordIfVersion(@Param("id") UUID id, @Param("authVersion") long authVersion,
                                @Param("passwordHash") String passwordHash);
    int updateRole(@Param("id") UUID id, @Param("role") String role);
    int updateEnabled(@Param("id") UUID id, @Param("enabled") boolean enabled);
}
