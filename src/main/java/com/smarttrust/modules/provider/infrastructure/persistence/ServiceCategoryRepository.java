package com.smarttrust.modules.provider.infrastructure.persistence;

import com.smarttrust.modules.provider.domain.entity.ServiceCategory;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface ServiceCategoryRepository extends JpaRepository<ServiceCategory, Long> {
    Optional<ServiceCategory> findByNameIgnoreCase(String name);
    boolean existsByNameIgnoreCase(String name);
    List<ServiceCategory> findByActiveTrueOrderByNameAsc();
}
