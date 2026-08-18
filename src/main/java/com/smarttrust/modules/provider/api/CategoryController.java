package com.smarttrust.modules.provider.api;

import com.smarttrust.modules.provider.api.dto.CategoryResponse;
import com.smarttrust.modules.provider.domain.service.ServiceProviderService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/categories")
@RequiredArgsConstructor
@Tag(name = "Categories", description = "Public list of service categories (for the provider form)")
public class CategoryController {

    private final ServiceProviderService providerService;

    @Operation(summary = "List active service categories")
    @GetMapping
    public ResponseEntity<List<CategoryResponse>> listCategories() {
        return ResponseEntity.ok(providerService.listActiveCategories());
    }
}
