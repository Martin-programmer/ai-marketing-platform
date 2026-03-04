package com.amp.clients;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface UserClientPermissionRepository
        extends JpaRepository<UserClientPermission, UUID> {

    List<UserClientPermission> findByUserId(UUID userId);

    List<UserClientPermission> findByClientId(UUID clientId);

    List<UserClientPermission> findByUserIdAndClientId(UUID userId, UUID clientId);

    @Query("SELECT DISTINCT p.clientId FROM UserClientPermission p WHERE p.userId = :userId")
    List<UUID> findClientIdsByUserId(UUID userId);

    @Query("SELECT DISTINCT p.permission FROM UserClientPermission p "
            + "WHERE p.userId = :userId AND p.clientId = :clientId")
    List<String> findPermissionsByUserIdAndClientId(UUID userId, UUID clientId);

    boolean existsByUserIdAndClientIdAndPermission(UUID userId, UUID clientId, String permission);

    @Modifying
    @Query("DELETE FROM UserClientPermission p WHERE p.userId = :userId AND p.clientId = :clientId")
    void deleteByUserIdAndClientId(@Param("userId") UUID userId, @Param("clientId") UUID clientId);

    @Modifying
    @Query("DELETE FROM UserClientPermission p WHERE p.userId = :userId")
    void deleteByUserId(@Param("userId") UUID userId);

    @Modifying
    @Query("DELETE FROM UserClientPermission p WHERE p.clientId = :clientId")
    void deleteAllByClientId(@Param("clientId") UUID clientId);

    List<UserClientPermission> findAllByClientId(UUID clientId);
}
