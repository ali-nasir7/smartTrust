package com.smarttrust.modules.auth.domain.service;

import com.smarttrust.modules.auth.api.dto.RegisterInitResponse;
import com.smarttrust.modules.auth.domain.entity.OtpCode;
import com.smarttrust.modules.auth.domain.enums.OtpPurpose;

public interface OtpService {

    OtpCode generateAndSaveOtp(String phone, OtpPurpose purpose);

    OtpCode getLatestValidOtp(String phone, OtpPurpose purpose);

    void verifyOtp(OtpCode otpCode, String plainOtp);

    void incrementAttempts(OtpCode otpCode);

    void markVerified(OtpCode otpCode);
}
