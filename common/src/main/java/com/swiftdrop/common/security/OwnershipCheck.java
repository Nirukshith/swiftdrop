package com.swiftdrop.common.security;

import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Component;

@Component
public class OwnershipCheck {

    /**
     * Checks if the currently authenticated user has the specified owner user ID.
     */
    public boolean isOwner(String ownerId) {
        if (ownerId == null || ownerId.isBlank()) {
            return false;
        }
        return CurrentUser.get()
            .map(u -> ownerId.equals(u.userId()))
            .orElse(false);
    }

    /**
     * Asserts that the currently authenticated user is the resource owner.
     * Throws AccessDeniedException if not.
     */
    public void checkOwner(String ownerId) {
        if (!isOwner(ownerId)) {
            throw new AccessDeniedException("Access denied: current user is not the owner of this resource");
        }
    }

    /**
     * Checks if the currently authenticated user is either the resource owner OR an administrator/dispatcher.
     */
    public boolean isOwnerOrAdmin(String ownerId) {
        if (ownerId == null || ownerId.isBlank()) {
            return false;
        }
        return CurrentUser.get()
            .map(u -> ownerId.equals(u.userId()) || u.isAdmin() || u.isDispatcher())
            .orElse(false);
    }

    /**
     * Asserts that the currently authenticated user is either the owner or an administrator.
     * Throws AccessDeniedException if not.
     */
    public void checkOwnerOrAdmin(String ownerId) {
        if (!isOwnerOrAdmin(ownerId)) {
            throw new AccessDeniedException("Access denied: current user is neither resource owner nor administrator");
        }
    }

    /**
     * Checks if the currently authenticated user is a courier assigned to courierId.
     */
    public boolean isCourierOwner(String courierId) {
        if (courierId == null || courierId.isBlank()) {
            return false;
        }
        return CurrentUser.get()
            .map(u -> u.isCourier() && courierId.equals(u.userId()))
            .orElse(false);
    }

    /**
     * Asserts that the currently authenticated courier matches the assigned courier ID.
     * Throws AccessDeniedException if not.
     */
    public void checkCourierOwner(String courierId) {
        if (!isCourierOwner(courierId)) {
            throw new AccessDeniedException("Access denied: courier may only access and update their own assigned deliveries");
        }
    }

    /**
     * Checks if the currently authenticated user is associated with the given merchantId (or is Admin).
     */
    public boolean isMerchantOwner(String merchantId) {
        if (merchantId == null || merchantId.isBlank()) {
            return false;
        }
        return CurrentUser.get()
            .map(u -> (u.isMerchant() && merchantId.equals(u.merchantId())) || u.isAdmin())
            .orElse(false);
    }

    /**
     * Asserts that the currently authenticated user matches the merchantId.
     * Throws AccessDeniedException if not.
     */
    public void checkMerchantOwner(String merchantId) {
        if (!isMerchantOwner(merchantId)) {
            throw new AccessDeniedException("Access denied: merchant may only access their own resources");
        }
    }
}
