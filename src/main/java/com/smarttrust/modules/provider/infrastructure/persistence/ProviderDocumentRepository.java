package com.smarttrust.modules.provider.infrastructure.persistence;

import com.smarttrust.modules.provider.domain.entity.ProviderDocument;
import com.smarttrust.modules.provider.domain.enums.ProviderDocumentType;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface ProviderDocumentRepository extends JpaRepository<ProviderDocument, Long> {
    List<ProviderDocument> findByUserIdOrderByDocTypeAsc(Long userId);
    Optional<ProviderDocument> findByUserIdAndDocType(Long userId, ProviderDocumentType docType);
    Optional<ProviderDocument> findByIdAndUserId(Long id, Long userId);
    void deleteByUserId(Long userId);
}
