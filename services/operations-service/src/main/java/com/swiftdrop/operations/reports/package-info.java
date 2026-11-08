/**
 * Daily delivery reporting built from parcel lifecycle events.
 *
 * <p>A delivery attempt is counted only when a parcel reaches DELIVERED or
 * FAILED. DELIVERED is successful, FAILED is failed, and CANCELLED is
 * excluded. A FAILED to CREATED re-queue adds no count; later terminal
 * attempts are counted independently using the terminal event's
 * occurredAt converted to Asia/Colombo.</p>
 */
package com.swiftdrop.operations.reports;
