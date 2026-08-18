package com.smarttrust.modules.customer.infrastructure.persistence;

import com.smarttrust.modules.customer.domain.entity.CustomerProfile;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface CustomerProfileRepository extends JpaRepository<CustomerProfile, Long> {
    Optional<CustomerProfile> findByUserId(Long userId);
    boolean existsByUserId(Long userId);
}
