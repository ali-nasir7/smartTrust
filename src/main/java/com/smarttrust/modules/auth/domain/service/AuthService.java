package com.smarttrust.modules.auth.domain.service;

import com.smarttrust.modules.auth.api.dto.*;
import jakarta.servlet.http.HttpServletRequest;

public interface AuthService {

    RegisterInitResponse registerInit(RegisterInitRequest request, HttpServletRequest httpRequest);

    AuthResponse verifyOtp(VerifyOtpRequest request, HttpServletRequest httpRequest);

    AuthResponse login(LoginRequest request, HttpServletRequest httpRequest);

    AuthResponse refresh(RefreshRequest request, HttpServletRequest httpRequest);

    void logout(RefreshRequest request, HttpServletRequest httpRequest);

    RegisterInitResponse forgotPasswordInit(ForgotPasswordInitRequest request);

    void forgotPasswordReset(ForgotPasswordResetRequest request);
}
