package com.smarttrust.modules.customer.domain.service;

import com.smarttrust.common.exception.BusinessException;
import com.smarttrust.common.exception.ErrorCode;
import com.smarttrust.modules.customer.api.dto.CustomerProfileRequest;
import com.smarttrust.modules.customer.api.dto.CustomerProfileResponse;
import com.smarttrust.modules.customer.domain.entity.CustomerProfile;
import com.smarttrust.modules.customer.infrastructure.persistence.CustomerProfileRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class CustomerProfileService {

    private final CustomerProfileRepository customerProfileRepository;

    /** Creates or updates the customer's own profile (upsert by user). */
    @Transactional
    public CustomerProfileResponse upsertMyProfile(Long userId, CustomerProfileRequest request) {
        CustomerProfile profile = customerProfileRepository.findByUserId(userId)
                .orElseGet(() -> CustomerProfile.builder().userId(userId).build());

        profile.setFullName(request.fullName().trim());
        profile.setAddress(request.address().trim());
        profile.setCity(request.city().trim());

        CustomerProfile saved = customerProfileRepository.save(profile);
        log.info("Customer profile saved userId={} profileId={}", userId, saved.getId());
        return toResponse(saved);
    }

    @Transactional(readOnly = true)
    public CustomerProfileResponse getMyProfile(Long userId) {
        return customerProfileRepository.findByUserId(userId)
                .map(CustomerProfileService::toResponse)
                .orElseThrow(() -> BusinessException.of(ErrorCode.CUSTOMER_PROFILE_NOT_FOUND, HttpStatus.NOT_FOUND,
                        "Customer profile not completed yet"));
    }

    private static CustomerProfileResponse toResponse(CustomerProfile p) {
        return new CustomerProfileResponse(
                p.getId(), p.getUserId(), p.getFullName(), p.getAddress(), p.getCity(),
                p.getCreatedAt(), p.getUpdatedAt());
    }
}
