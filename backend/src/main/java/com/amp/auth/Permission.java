package com.amp.auth;

/**
 * Granular permissions that can be assigned to AGENCY_USER per client.
 * <p>
 * OWNER_ADMIN and AGENCY_ADMIN bypass these — they have full access.
 */
public enum Permission {

    CLIENT_VIEW,
    CLIENT_EDIT,

    CAMPAIGNS_VIEW,
    CAMPAIGNS_EDIT,
    CAMPAIGNS_PUBLISH,

    CREATIVES_VIEW,
    CREATIVES_EDIT,

    REPORTS_VIEW,
    REPORTS_EDIT,
    REPORTS_SEND,

    META_MANAGE,

    AI_VIEW,
    AI_APPROVE;

    /** All permissions — used when granting AGENCY_ADMIN-equivalent access. */
    public static Permission[] all() {
        return values();
    }

    /** Read-only permissions bundle. */
    public static Permission[] readOnly() {
        return new Permission[]{
            CLIENT_VIEW, CAMPAIGNS_VIEW, CREATIVES_VIEW,
            REPORTS_VIEW, AI_VIEW
        };
    }

    /** Standard editor permissions bundle. */
    public static Permission[] editor() {
        return new Permission[]{
            CLIENT_VIEW, CLIENT_EDIT,
            CAMPAIGNS_VIEW, CAMPAIGNS_EDIT,
            CREATIVES_VIEW, CREATIVES_EDIT,
            REPORTS_VIEW, REPORTS_EDIT,
            AI_VIEW, AI_APPROVE
        };
    }
}
