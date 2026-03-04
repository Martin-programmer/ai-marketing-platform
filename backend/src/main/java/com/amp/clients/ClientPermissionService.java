package com.amp.clients;

import com.amp.auth.Permission;
import com.amp.auth.UserAccount;
import com.amp.auth.UserAccountRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Service managing user-client permissions.
 * <p>
 * Permissions are granular values from {@link Permission} enum.
 */
@Service
@Transactional
public class ClientPermissionService {

    private final UserClientPermissionRepository permissionRepository;
    private final UserAccountRepository userAccountRepository;

    public ClientPermissionService(UserClientPermissionRepository permissionRepository,
                                   UserAccountRepository userAccountRepository) {
        this.permissionRepository = permissionRepository;
        this.userAccountRepository = userAccountRepository;
    }

    @Transactional(readOnly = true)
    public List<PermissionResponse> listPermissions(UUID clientId) {
        List<UserClientPermission> perms = permissionRepository.findAllByClientId(clientId);
        if (perms.isEmpty()) return List.of();

        Set<UUID> userIds = perms.stream()
                .map(UserClientPermission::getUserId)
                .collect(Collectors.toSet());

        Map<UUID, UserAccount> usersMap = userAccountRepository.findAllById(userIds)
                .stream()
                .collect(Collectors.toMap(UserAccount::getId, Function.identity()));

        return perms.stream().map(p -> {
            UserAccount u = usersMap.get(p.getUserId());
            return new PermissionResponse(
                    p.getUserId(),
                    u != null ? u.getEmail() : "unknown",
                    u != null ? u.getDisplayName() : "unknown",
                    p.getPermission()
            );
        }).toList();
    }

    public List<PermissionResponse> replacePermissions(UUID clientId,
                                                       List<AddPermissionRequest> requests) {
        permissionRepository.deleteAllByClientId(clientId);
        permissionRepository.flush();

        for (AddPermissionRequest req : requests) {
            validatePermission(req.permission());
            UserClientPermission p = new UserClientPermission(
                    req.userId(), clientId, req.permission());
            permissionRepository.save(p);
        }

        return listPermissions(clientId);
    }

    public PermissionResponse addPermission(UUID clientId, AddPermissionRequest req) {
        validatePermission(req.permission());

        UserClientPermission p = new UserClientPermission(
                req.userId(), clientId, req.permission());
        permissionRepository.save(p);

        UserAccount u = userAccountRepository.findById(req.userId()).orElse(null);
        return new PermissionResponse(
                req.userId(),
                u != null ? u.getEmail() : "unknown",
                u != null ? u.getDisplayName() : "unknown",
                req.permission()
        );
    }

    public void removeUserPermissions(UUID clientId, UUID userId) {
        permissionRepository.deleteByUserIdAndClientId(userId, clientId);
    }

    /**
     * Replace all permissions for a user-client pair.
     * Called from PermissionController.
     */
    public void setUserClientPermissions(UUID userId, UUID clientId,
                                         List<String> permissions, UUID grantedBy) {
        for (String p : permissions) {
            validatePermission(p);
        }
        permissionRepository.deleteByUserIdAndClientId(userId, clientId);
        permissionRepository.flush();

        for (String perm : permissions) {
            UserClientPermission ucp = new UserClientPermission(userId, clientId, perm, grantedBy);
            permissionRepository.save(ucp);
        }
    }

    private void validatePermission(String permission) {
        try {
            Permission.valueOf(permission);
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Invalid permission: " + permission
                    + ". Valid values: " + Arrays.toString(Permission.values()));
        }
    }
}
