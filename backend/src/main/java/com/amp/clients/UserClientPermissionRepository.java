package com.amp.clients;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface UserClientPermissionRepository
        extends JpaRepository<UserClientPermission, UserClientPermission.PK> {

    List<UserClientPermission> findAllByClientId(UUID clientId);

    void deleteAllByClientId(UUID clientId);

    void deleteByUserIdAndClientId(UUID userId, UUID clientId);
}
