package com.smarttrust.modules.provider.infrastructure.persistence;

import com.smarttrust.modules.provider.domain.entity.ServiceProviderProfile;
import com.smarttrust.modules.provider.domain.enums.ProviderVerificationStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface ServiceProviderProfileRepository extends JpaRepository<ServiceProviderProfile, Long> {
    Optional<ServiceProviderProfile> findByUserId(Long userId);

    Page<ServiceProviderProfile> findByVerificationStatus(ProviderVerificationStatus status, Pageable pageable);

    Page<ServiceProviderProfile> findAllByOrderByUpdatedAtDesc(Pageable pageable);
}
