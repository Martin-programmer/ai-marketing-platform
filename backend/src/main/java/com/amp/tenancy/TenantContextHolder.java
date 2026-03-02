package com.amp.tenancy;

/**
 * Thread-local storage for the current request's {@link TenantContext}.
 * <p>
 * Must be {@link #clear() cleared} at the end of every request
 * to prevent context leaking between threads.
 */
public final class TenantContextHolder {

    private static final ThreadLocal<TenantContext> CONTEXT = new ThreadLocal<>();

    private TenantContextHolder() {
        // utility class
    }

    public static void set(TenantContext ctx) {
        CONTEXT.set(ctx);
    }

    public static TenantContext get() {
        return CONTEXT.get();
    }

    /**
     * Returns the current context or throws if none is set.
     */
    public static TenantContext require() {
        TenantContext ctx = CONTEXT.get();
        if (ctx == null) {
            throw new IllegalStateException("No tenant context set for current request");
        }
        return ctx;
    }

    public static void clear() {
        CONTEXT.remove();
    }
}
