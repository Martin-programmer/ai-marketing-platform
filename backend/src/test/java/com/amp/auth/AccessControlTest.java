package com.amp.auth;

import com.amp.clients.UserClientPermissionRepository;
import com.amp.tenancy.TenantContext;
import com.amp.tenancy.TenantContextHolder;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.access.AccessDeniedException;

import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

/**
 * Unit tests for {@link AccessControl} — the central authorization service.
 * <p>
 * Covers every role in the hierarchy:
 * OWNER_ADMIN → AGENCY_ADMIN → AGENCY_USER → CLIENT_USER
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("AccessControl — Permission & Role Tests")
class AccessControlTest {

    private static final UUID AGENCY_ID    = UUID.fromString("00000000-0000-0000-0000-000000000001");
    private static final UUID OTHER_AGENCY = UUID.fromString("00000000-0000-0000-0000-000000000099");
    private static final UUID CLIENT_ID    = UUID.fromString("00000000-0000-0000-0000-000000000100");
    private static final UUID CLIENT_ID_2  = UUID.fromString("00000000-0000-0000-0000-000000000200");
    private static final UUID USER_ID      = UUID.fromString("00000000-0000-0000-0000-000000000010");

    @Mock
    private UserClientPermissionRepository permissionRepository;

    @InjectMocks
    private AccessControl accessControl;

    @AfterEach
    void clearContext() {
        TenantContextHolder.clear();
    }

    private void setContext(UUID agencyId, UUID userId, String role) {
        TenantContextHolder.set(new TenantContext(agencyId, userId, "test@local", role));
    }

    private void setContext(UUID agencyId, UUID userId, String role, UUID clientId) {
        TenantContextHolder.set(new TenantContext(agencyId, userId, "test@local", role, clientId));
    }

    // ══════════════════════════════════════════════════════════════════════
    // OWNER_ADMIN — platform superadmin, null agency, sees everything
    // ══════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("OWNER_ADMIN")
    class OwnerAdminTests {

        @Test
        @DisplayName("can access any client with any permission")
        void ownerAdmin_accessAnyClient() {
            setContext(null, USER_ID, "OWNER_ADMIN");

            // Should NOT throw for any permission on any client
            for (Permission perm : Permission.values()) {
                accessControl.requireClientPermission(CLIENT_ID, perm);
                accessControl.requireClientPermission(CLIENT_ID_2, perm);
            }
            verifyNoInteractions(permissionRepository);
        }

        @Test
        @DisplayName("can access any agency")
        void ownerAdmin_accessAnyAgency() {
            setContext(null, USER_ID, "OWNER_ADMIN");

            accessControl.requireAgencyAccess(AGENCY_ID);
            accessControl.requireAgencyAccess(OTHER_AGENCY);
            // No exception
        }

        @Test
        @DisplayName("isOwner returns true")
        void ownerAdmin_isOwner() {
            setContext(null, USER_ID, "OWNER_ADMIN");
            assertThat(accessControl.isOwner()).isTrue();
            assertThat(accessControl.isAgencyAdmin()).isFalse();
        }

        @Test
        @DisplayName("isAgencyLevel returns true")
        void ownerAdmin_isAgencyLevel() {
            setContext(null, USER_ID, "OWNER_ADMIN");
            assertThat(accessControl.isAgencyLevel()).isTrue();
        }

        @Test
        @DisplayName("accessibleClientIds returns null (all clients)")
        void ownerAdmin_accessibleClientsNull() {
            setContext(null, USER_ID, "OWNER_ADMIN");
            assertThat(accessControl.accessibleClientIds()).isNull();
        }

        @Test
        @DisplayName("requireAgencyRole passes")
        void ownerAdmin_requireAgencyRole() {
            setContext(null, USER_ID, "OWNER_ADMIN");
            accessControl.requireAgencyRole(); // No exception
        }

        @Test
        @DisplayName("currentAgencyId returns null")
        void ownerAdmin_currentAgencyIdNull() {
            setContext(null, USER_ID, "OWNER_ADMIN");
            assertThat(accessControl.currentAgencyId()).isNull();
        }
    }

    // ══════════════════════════════════════════════════════════════════════
    // AGENCY_ADMIN — full access within own agency
    // ══════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("AGENCY_ADMIN")
    class AgencyAdminTests {

        @Test
        @DisplayName("can access all clients in own agency — all permissions")
        void agencyAdmin_fullAccess() {
            setContext(AGENCY_ID, USER_ID, "AGENCY_ADMIN");

            for (Permission perm : Permission.values()) {
                accessControl.requireClientPermission(CLIENT_ID, perm);
            }
            verifyNoInteractions(permissionRepository);
        }

        @Test
        @DisplayName("can access own agency")
        void agencyAdmin_ownAgency() {
            setContext(AGENCY_ID, USER_ID, "AGENCY_ADMIN");
            accessControl.requireAgencyAccess(AGENCY_ID); // OK
        }

        @Test
        @DisplayName("cannot access other agency")
        void agencyAdmin_otherAgencyBlocked() {
            setContext(AGENCY_ID, USER_ID, "AGENCY_ADMIN");
            assertThatThrownBy(() -> accessControl.requireAgencyAccess(OTHER_AGENCY))
                    .isInstanceOf(AccessDeniedException.class);
        }

        @Test
        @DisplayName("isAgencyAdmin returns true")
        void agencyAdmin_isAgencyAdmin() {
            setContext(AGENCY_ID, USER_ID, "AGENCY_ADMIN");
            assertThat(accessControl.isAgencyAdmin()).isTrue();
            assertThat(accessControl.isOwner()).isFalse();
        }

        @Test
        @DisplayName("accessibleClientIds returns null (all clients in agency)")
        void agencyAdmin_accessibleClientsNull() {
            setContext(AGENCY_ID, USER_ID, "AGENCY_ADMIN");
            assertThat(accessControl.accessibleClientIds()).isNull();
        }
    }

    // ══════════════════════════════════════════════════════════════════════
    // AGENCY_USER — only assigned clients with specific permissions
    // ══════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("AGENCY_USER")
    class AgencyUserTests {

        @Test
        @DisplayName("with CAMPAIGNS_VIEW can view campaigns")
        void agencyUser_withCampaignsView() {
            setContext(AGENCY_ID, USER_ID, "AGENCY_USER");
            when(permissionRepository.existsByUserIdAndClientIdAndPermission(
                    USER_ID, CLIENT_ID, "CAMPAIGNS_VIEW")).thenReturn(true);

            accessControl.requireClientPermission(CLIENT_ID, Permission.CAMPAIGNS_VIEW);
            // No exception
        }

        @Test
        @DisplayName("without CAMPAIGNS_EDIT cannot edit campaigns — 403")
        void agencyUser_withoutCampaignsEdit() {
            setContext(AGENCY_ID, USER_ID, "AGENCY_USER");
            when(permissionRepository.existsByUserIdAndClientIdAndPermission(
                    USER_ID, CLIENT_ID, "CAMPAIGNS_EDIT")).thenReturn(false);            when(permissionRepository.findPermissionsByUserIdAndClientId(
                    USER_ID, CLIENT_ID)).thenReturn(List.of());
            assertThatThrownBy(() ->
                    accessControl.requireClientPermission(CLIENT_ID, Permission.CAMPAIGNS_EDIT))
                    .isInstanceOf(AccessDeniedException.class)
                    .hasMessageContaining("CAMPAIGNS_EDIT");
        }

        @Test
        @DisplayName("with CAMPAIGNS_EDIT can edit campaigns")
        void agencyUser_withCampaignsEdit() {
            setContext(AGENCY_ID, USER_ID, "AGENCY_USER");
            when(permissionRepository.existsByUserIdAndClientIdAndPermission(
                    USER_ID, CLIENT_ID, "CAMPAIGNS_EDIT")).thenReturn(true);

            accessControl.requireClientPermission(CLIENT_ID, Permission.CAMPAIGNS_EDIT);
            // No exception
        }

        @Test
        @DisplayName("without any permission for client — 403")
        void agencyUser_noPermission() {
            setContext(AGENCY_ID, USER_ID, "AGENCY_USER");
            when(permissionRepository.existsByUserIdAndClientIdAndPermission(
                    USER_ID, CLIENT_ID, "CLIENT_VIEW")).thenReturn(false);            when(permissionRepository.findPermissionsByUserIdAndClientId(
                    USER_ID, CLIENT_ID)).thenReturn(List.of());
            assertThatThrownBy(() ->
                    accessControl.requireClientPermission(CLIENT_ID, Permission.CLIENT_VIEW))
                    .isInstanceOf(AccessDeniedException.class);
        }

        @Test
        @DisplayName("accessibleClientIds returns assigned client list")
        void agencyUser_accessibleClients() {
            setContext(AGENCY_ID, USER_ID, "AGENCY_USER");
            when(permissionRepository.findClientIdsByUserId(USER_ID))
                    .thenReturn(List.of(CLIENT_ID, CLIENT_ID_2));

            List<UUID> ids = accessControl.accessibleClientIds();
            assertThat(ids).containsExactlyInAnyOrder(CLIENT_ID, CLIENT_ID_2);
        }

        @Test
        @DisplayName("accessibleClientIds returns empty when no assignments")
        void agencyUser_noAssignments() {
            setContext(AGENCY_ID, USER_ID, "AGENCY_USER");
            when(permissionRepository.findClientIdsByUserId(USER_ID))
                    .thenReturn(List.of());

            List<UUID> ids = accessControl.accessibleClientIds();
            assertThat(ids).isEmpty();
        }

        @Test
        @DisplayName("isAgencyLevel returns true")
        void agencyUser_isAgencyLevel() {
            setContext(AGENCY_ID, USER_ID, "AGENCY_USER");
            assertThat(accessControl.isAgencyLevel()).isTrue();
            assertThat(accessControl.isAgencyUser()).isTrue();
        }

        @Test
        @DisplayName("requireAgencyRole passes")
        void agencyUser_requireAgencyRole() {
            setContext(AGENCY_ID, USER_ID, "AGENCY_USER");
            accessControl.requireAgencyRole(); // No exception
        }

        @Test
        @DisplayName("canAccessClient delegates to CLIENT_VIEW check")
        void agencyUser_canAccessClient() {
            setContext(AGENCY_ID, USER_ID, "AGENCY_USER");
            when(permissionRepository.existsByUserIdAndClientIdAndPermission(
                    USER_ID, CLIENT_ID, "CLIENT_VIEW")).thenReturn(true);

            assertThat(accessControl.canAccessClient(CLIENT_ID)).isTrue();
        }

        @Test
        @DisplayName("canAccessClient returns false without CLIENT_VIEW")
        void agencyUser_cannotAccessClient() {
            setContext(AGENCY_ID, USER_ID, "AGENCY_USER");
            when(permissionRepository.existsByUserIdAndClientIdAndPermission(
                    USER_ID, CLIENT_ID, "CLIENT_VIEW")).thenReturn(false);
            when(permissionRepository.findPermissionsByUserIdAndClientId(
                    USER_ID, CLIENT_ID)).thenReturn(List.of());

            assertThat(accessControl.canAccessClient(CLIENT_ID)).isFalse();
        }
    }

    // ══════════════════════════════════════════════════════════════════════
    // CLIENT_USER — read-only portal access for own client
    // ══════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("CLIENT_USER")
    class ClientUserTests {

        @Test
        @DisplayName("can view own client (CLIENT_VIEW)")
        void clientUser_viewOwnClient() {
            setContext(AGENCY_ID, USER_ID, "CLIENT_USER", CLIENT_ID);
            accessControl.requireClientPermission(CLIENT_ID, Permission.CLIENT_VIEW);
            // No exception
        }

        @Test
        @DisplayName("can view own client campaigns (CAMPAIGNS_VIEW)")
        void clientUser_viewOwnCampaigns() {
            setContext(AGENCY_ID, USER_ID, "CLIENT_USER", CLIENT_ID);
            accessControl.requireClientPermission(CLIENT_ID, Permission.CAMPAIGNS_VIEW);
        }

        @Test
        @DisplayName("can view own client reports (REPORTS_VIEW)")
        void clientUser_viewOwnReports() {
            setContext(AGENCY_ID, USER_ID, "CLIENT_USER", CLIENT_ID);
            accessControl.requireClientPermission(CLIENT_ID, Permission.REPORTS_VIEW);
        }

        @Test
        @DisplayName("cannot edit own client (CLIENT_EDIT) — 403")
        void clientUser_cannotEdit() {
            setContext(AGENCY_ID, USER_ID, "CLIENT_USER", CLIENT_ID);
            assertThatThrownBy(() ->
                    accessControl.requireClientPermission(CLIENT_ID, Permission.CLIENT_EDIT))
                    .isInstanceOf(AccessDeniedException.class);
        }

        @Test
        @DisplayName("cannot access other client — 403")
        void clientUser_otherClientBlocked() {
            setContext(AGENCY_ID, USER_ID, "CLIENT_USER", CLIENT_ID);
            assertThatThrownBy(() ->
                    accessControl.requireClientPermission(CLIENT_ID_2, Permission.CLIENT_VIEW))
                    .isInstanceOf(AccessDeniedException.class);
        }

        @Test
        @DisplayName("cannot manage campaigns (CAMPAIGNS_EDIT) — 403")
        void clientUser_cannotManageCampaigns() {
            setContext(AGENCY_ID, USER_ID, "CLIENT_USER", CLIENT_ID);
            assertThatThrownBy(() ->
                    accessControl.requireClientPermission(CLIENT_ID, Permission.CAMPAIGNS_EDIT))
                    .isInstanceOf(AccessDeniedException.class);
        }

        @Test
        @DisplayName("cannot publish campaigns — 403")
        void clientUser_cannotPublishCampaigns() {
            setContext(AGENCY_ID, USER_ID, "CLIENT_USER", CLIENT_ID);
            assertThatThrownBy(() ->
                    accessControl.requireClientPermission(CLIENT_ID, Permission.CAMPAIGNS_PUBLISH))
                    .isInstanceOf(AccessDeniedException.class);
        }

        @Test
        @DisplayName("cannot manage Meta — 403")
        void clientUser_cannotManageMeta() {
            setContext(AGENCY_ID, USER_ID, "CLIENT_USER", CLIENT_ID);
            assertThatThrownBy(() ->
                    accessControl.requireClientPermission(CLIENT_ID, Permission.META_MANAGE))
                    .isInstanceOf(AccessDeniedException.class);
        }

        @Test
        @DisplayName("isClientUser returns true")
        void clientUser_isClientUser() {
            setContext(AGENCY_ID, USER_ID, "CLIENT_USER", CLIENT_ID);
            assertThat(accessControl.isClientUser()).isTrue();
            assertThat(accessControl.isAgencyLevel()).isFalse();
        }

        @Test
        @DisplayName("requireAgencyRole fails — 403")
        void clientUser_requireAgencyRoleFails() {
            setContext(AGENCY_ID, USER_ID, "CLIENT_USER", CLIENT_ID);
            assertThatThrownBy(() -> accessControl.requireAgencyRole())
                    .isInstanceOf(AccessDeniedException.class);
        }

        @Test
        @DisplayName("accessibleClientIds returns single own client")
        void clientUser_accessibleClients() {
            setContext(AGENCY_ID, USER_ID, "CLIENT_USER", CLIENT_ID);
            List<UUID> ids = accessControl.accessibleClientIds();
            assertThat(ids).containsExactly(CLIENT_ID);
        }

        @Test
        @DisplayName("accessibleClientIds returns empty when no clientId set")
        void clientUser_noClientId() {
            setContext(AGENCY_ID, USER_ID, "CLIENT_USER", null);
            List<UUID> ids = accessControl.accessibleClientIds();
            assertThat(ids).isEmpty();
        }
    }

    // ══════════════════════════════════════════════════════════════════════
    // Permission presets
    // ══════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("Permission Presets")
    class PermissionPresetTests {

        @Test
        @DisplayName("readOnly preset has 5 view permissions")
        void readOnlyPreset() {
            Permission[] ro = Permission.readOnly();
            assertThat(ro).hasSize(5);
            assertThat(ro).containsExactlyInAnyOrder(
                    Permission.CLIENT_VIEW,
                    Permission.CAMPAIGNS_VIEW,
                    Permission.CREATIVES_VIEW,
                    Permission.REPORTS_VIEW,
                    Permission.AI_VIEW
            );
        }

        @Test
        @DisplayName("editor preset has correct permissions (no publish/send/meta)")
        void editorPreset() {
            Permission[] ed = Permission.editor();
            assertThat(ed).hasSize(10);
            assertThat(ed).contains(
                    Permission.CLIENT_VIEW, Permission.CLIENT_EDIT,
                    Permission.CAMPAIGNS_VIEW, Permission.CAMPAIGNS_EDIT,
                    Permission.CREATIVES_VIEW, Permission.CREATIVES_EDIT,
                    Permission.REPORTS_VIEW, Permission.REPORTS_EDIT,
                    Permission.AI_VIEW, Permission.AI_APPROVE
            );
            assertThat(ed).doesNotContain(
                    Permission.CAMPAIGNS_PUBLISH,
                    Permission.REPORTS_SEND,
                    Permission.META_MANAGE
            );
        }

        @Test
        @DisplayName("full access preset has all 13 permissions")
        void fullAccessPreset() {
            Permission[] all = Permission.all();
            assertThat(all).hasSize(13);
            assertThat(all).containsExactlyInAnyOrder(Permission.values());
        }
    }

    // ══════════════════════════════════════════════════════════════════════
    // Permission Inheritance
    // ══════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("Permission Inheritance")
    class PermissionInheritanceTests {

        @Test
        @DisplayName("CAMPAIGNS_EDIT implies CAMPAIGNS_VIEW")
        void campaignsEdit_impliesCampaignsView() {
            setContext(AGENCY_ID, USER_ID, "AGENCY_USER");
            // User does NOT have CAMPAIGNS_VIEW directly
            when(permissionRepository.existsByUserIdAndClientIdAndPermission(
                    USER_ID, CLIENT_ID, "CAMPAIGNS_VIEW")).thenReturn(false);
            // User has CAMPAIGNS_EDIT
            when(permissionRepository.findPermissionsByUserIdAndClientId(
                    USER_ID, CLIENT_ID)).thenReturn(List.of("CAMPAIGNS_EDIT"));

            // Should NOT throw — inherited from CAMPAIGNS_EDIT
            accessControl.requireClientPermission(CLIENT_ID, Permission.CAMPAIGNS_VIEW);
        }

        @Test
        @DisplayName("CLIENT_EDIT implies CLIENT_VIEW")
        void clientEdit_impliesClientView() {
            setContext(AGENCY_ID, USER_ID, "AGENCY_USER");
            when(permissionRepository.existsByUserIdAndClientIdAndPermission(
                    USER_ID, CLIENT_ID, "CLIENT_VIEW")).thenReturn(false);
            when(permissionRepository.findPermissionsByUserIdAndClientId(
                    USER_ID, CLIENT_ID)).thenReturn(List.of("CLIENT_EDIT"));

            accessControl.requireClientPermission(CLIENT_ID, Permission.CLIENT_VIEW);
        }

        @Test
        @DisplayName("CAMPAIGNS_PUBLISH implies both CAMPAIGNS_VIEW and CAMPAIGNS_EDIT")
        void campaignsPublish_impliesViewAndEdit() {
            setContext(AGENCY_ID, USER_ID, "AGENCY_USER");

            // Check CAMPAIGNS_VIEW is implied
            when(permissionRepository.existsByUserIdAndClientIdAndPermission(
                    USER_ID, CLIENT_ID, "CAMPAIGNS_VIEW")).thenReturn(false);
            when(permissionRepository.findPermissionsByUserIdAndClientId(
                    USER_ID, CLIENT_ID)).thenReturn(List.of("CAMPAIGNS_PUBLISH"));
            accessControl.requireClientPermission(CLIENT_ID, Permission.CAMPAIGNS_VIEW);

            // Check CAMPAIGNS_EDIT is implied
            when(permissionRepository.existsByUserIdAndClientIdAndPermission(
                    USER_ID, CLIENT_ID, "CAMPAIGNS_EDIT")).thenReturn(false);
            accessControl.requireClientPermission(CLIENT_ID, Permission.CAMPAIGNS_EDIT);
        }

        @Test
        @DisplayName("REPORTS_SEND implies REPORTS_VIEW and REPORTS_EDIT")
        void reportsSend_impliesViewAndEdit() {
            setContext(AGENCY_ID, USER_ID, "AGENCY_USER");

            when(permissionRepository.existsByUserIdAndClientIdAndPermission(
                    USER_ID, CLIENT_ID, "REPORTS_VIEW")).thenReturn(false);
            when(permissionRepository.findPermissionsByUserIdAndClientId(
                    USER_ID, CLIENT_ID)).thenReturn(List.of("REPORTS_SEND"));
            accessControl.requireClientPermission(CLIENT_ID, Permission.REPORTS_VIEW);

            when(permissionRepository.existsByUserIdAndClientIdAndPermission(
                    USER_ID, CLIENT_ID, "REPORTS_EDIT")).thenReturn(false);
            accessControl.requireClientPermission(CLIENT_ID, Permission.REPORTS_EDIT);
        }

        @Test
        @DisplayName("AI_APPROVE implies AI_VIEW")
        void aiApprove_impliesAiView() {
            setContext(AGENCY_ID, USER_ID, "AGENCY_USER");
            when(permissionRepository.existsByUserIdAndClientIdAndPermission(
                    USER_ID, CLIENT_ID, "AI_VIEW")).thenReturn(false);
            when(permissionRepository.findPermissionsByUserIdAndClientId(
                    USER_ID, CLIENT_ID)).thenReturn(List.of("AI_APPROVE"));

            accessControl.requireClientPermission(CLIENT_ID, Permission.AI_VIEW);
        }

        @Test
        @DisplayName("CREATIVES_EDIT implies CREATIVES_VIEW")
        void creativesEdit_impliesCreativesView() {
            setContext(AGENCY_ID, USER_ID, "AGENCY_USER");
            when(permissionRepository.existsByUserIdAndClientIdAndPermission(
                    USER_ID, CLIENT_ID, "CREATIVES_VIEW")).thenReturn(false);
            when(permissionRepository.findPermissionsByUserIdAndClientId(
                    USER_ID, CLIENT_ID)).thenReturn(List.of("CREATIVES_EDIT"));

            accessControl.requireClientPermission(CLIENT_ID, Permission.CREATIVES_VIEW);
        }

        @Test
        @DisplayName("META_MANAGE implies CLIENT_VIEW")
        void metaManage_impliesClientView() {
            setContext(AGENCY_ID, USER_ID, "AGENCY_USER");
            when(permissionRepository.existsByUserIdAndClientIdAndPermission(
                    USER_ID, CLIENT_ID, "CLIENT_VIEW")).thenReturn(false);
            when(permissionRepository.findPermissionsByUserIdAndClientId(
                    USER_ID, CLIENT_ID)).thenReturn(List.of("META_MANAGE"));

            accessControl.requireClientPermission(CLIENT_ID, Permission.CLIENT_VIEW);
        }

        @Test
        @DisplayName("direct permission still takes precedence (no extra DB call)")
        void directPermission_noInheritanceNeeded() {
            setContext(AGENCY_ID, USER_ID, "AGENCY_USER");
            when(permissionRepository.existsByUserIdAndClientIdAndPermission(
                    USER_ID, CLIENT_ID, "CAMPAIGNS_VIEW")).thenReturn(true);

            accessControl.requireClientPermission(CLIENT_ID, Permission.CAMPAIGNS_VIEW);

            // findPermissionsByUserIdAndClientId should NOT be called when direct check passes
            verify(permissionRepository, never()).findPermissionsByUserIdAndClientId(USER_ID, CLIENT_ID);
        }

        @Test
        @DisplayName("inheritance does not grant unrelated permissions")
        void inheritance_doesNotGrantUnrelated() {
            setContext(AGENCY_ID, USER_ID, "AGENCY_USER");
            // User has CAMPAIGNS_EDIT — should NOT imply REPORTS_VIEW
            when(permissionRepository.existsByUserIdAndClientIdAndPermission(
                    USER_ID, CLIENT_ID, "REPORTS_VIEW")).thenReturn(false);
            when(permissionRepository.findPermissionsByUserIdAndClientId(
                    USER_ID, CLIENT_ID)).thenReturn(List.of("CAMPAIGNS_EDIT"));

            assertThatThrownBy(() ->
                    accessControl.requireClientPermission(CLIENT_ID, Permission.REPORTS_VIEW))
                    .isInstanceOf(AccessDeniedException.class);
        }
    }

    // ══════════════════════════════════════════════════════════════════════
    // Effective Permissions (including inherited)
    // ══════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("Effective Permissions")
    class EffectivePermissionsTests {

        @Test
        @DisplayName("getEffectivePermissions includes inherited permissions")
        void effectivePermissions_includesInherited() {
            when(permissionRepository.findPermissionsByUserIdAndClientId(
                    USER_ID, CLIENT_ID)).thenReturn(List.of("CAMPAIGNS_EDIT", "REPORTS_SEND"));

            Set<String> effective = accessControl.getEffectivePermissions(USER_ID, CLIENT_ID);

            assertThat(effective).containsExactlyInAnyOrder(
                    "CAMPAIGNS_EDIT", "CAMPAIGNS_VIEW",     // CAMPAIGNS_EDIT → CAMPAIGNS_VIEW
                    "REPORTS_SEND", "REPORTS_VIEW", "REPORTS_EDIT"  // REPORTS_SEND → VIEW + EDIT
            );
        }

        @Test
        @DisplayName("getEffectivePermissions with no permissions returns empty")
        void effectivePermissions_empty() {
            when(permissionRepository.findPermissionsByUserIdAndClientId(
                    USER_ID, CLIENT_ID)).thenReturn(List.of());

            Set<String> effective = accessControl.getEffectivePermissions(USER_ID, CLIENT_ID);

            assertThat(effective).isEmpty();
        }

        @Test
        @DisplayName("getEffectivePermissions with non-inheriting permission returns only direct")
        void effectivePermissions_noInheritance() {
            when(permissionRepository.findPermissionsByUserIdAndClientId(
                    USER_ID, CLIENT_ID)).thenReturn(List.of("CLIENT_VIEW"));

            Set<String> effective = accessControl.getEffectivePermissions(USER_ID, CLIENT_ID);

            // CLIENT_VIEW has no implications
            assertThat(effective).containsExactly("CLIENT_VIEW");
        }

        @Test
        @DisplayName("getEffectivePermissions deduplicates overlapping inherited")
        void effectivePermissions_deduplicatesOverlap() {
            // CAMPAIGNS_PUBLISH implies VIEW + EDIT; CAMPAIGNS_EDIT implies VIEW
            when(permissionRepository.findPermissionsByUserIdAndClientId(
                    USER_ID, CLIENT_ID)).thenReturn(
                    List.of("CAMPAIGNS_PUBLISH", "CAMPAIGNS_EDIT"));

            Set<String> effective = accessControl.getEffectivePermissions(USER_ID, CLIENT_ID);

            assertThat(effective).containsExactlyInAnyOrder(
                    "CAMPAIGNS_PUBLISH", "CAMPAIGNS_EDIT", "CAMPAIGNS_VIEW"
            );
        }
    }
}
