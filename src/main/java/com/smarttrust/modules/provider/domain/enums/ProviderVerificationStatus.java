package com.smarttrust.modules.provider.domain.enums;

public enum ProviderVerificationStatus {
    /** Provider has not uploaded CNIC/selfie documents yet. */
    NOT_SUBMITTED,
    /** Documents submitted — waiting for admin review. */
    PENDING_REVIEW,
    /** Admin approved the documents — provider is Verified. */
    APPROVED,
    /** Admin rejected — rejectionReason stored; provider may update and resubmit. */
    REJECTED
}
