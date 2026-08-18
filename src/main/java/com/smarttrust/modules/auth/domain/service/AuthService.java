package com.smarttrust.modules.auth.domain.service;

import com.smarttrust.modules.auth.api.dto.*;
import jakarta.servlet.http.HttpServletRequest;

public interface AuthService {

    OtpSentResponse registerInit(RegisterInitRequest request, HttpServletRequest httpRequest);

    /** Resends the REGISTRATION OTP to the user's email (respects cooldown + rate limits). */
    OtpSentResponse resendRegistrationOtp(ResendOtpRequest request);

    /** Step after email verification: user selects CUSTOMER or SERVICE_PROVIDER. Returns fresh tokens. */
    AuthResponse selectRole(SelectRoleRequest request, HttpServletRequest httpRequest);

    AuthResponse verifyOtp(VerifyOtpRequest request, HttpServletRequest httpRequest);

    AuthResponse login(LoginRequest request, HttpServletRequest httpRequest);

    AuthResponse refresh(RefreshRequest request, HttpServletRequest httpRequest);

    void logout(RefreshRequest request, HttpServletRequest httpRequest);

    OtpSentResponse forgotPasswordInit(ForgotPasswordInitRequest request);

    void forgotPasswordReset(ForgotPasswordResetRequest request);
}
