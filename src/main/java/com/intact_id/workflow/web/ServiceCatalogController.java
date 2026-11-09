package com.intact_id.workflow.web;

import com.intact_id.dto.response.ApiResponse;
import com.intact_id.workflow.CatalogService;
import com.intact_id.workflow.ServiceCatalog;
import com.intact_id.workflow.ServiceRequirements;
import com.intact_id.workflow.ServiceStatus;
import com.intact_id.workflow.SubjectType;
import com.intact_id.workflow.catalog.CatalogAdminService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

import static com.intact_id.workflow.web.WorkflowResponses.respond;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/workflow/catalog")
@Tag(name = "Workflow service catalog", description = "Services workflows are built from. Intact staff manage it; merchants see AVAILABLE services.")
public class ServiceCatalogController {

    private final ServiceCatalog serviceCatalog;
    private final CatalogAdminService adminService;

    @GetMapping("/available")
    @PreAuthorize("hasRole('COMPANY') or hasRole('ADMIN') or hasRole('SUPER_ADMIN')")
    @Operation(summary = "Services a merchant can add to a workflow")
    public ResponseEntity<ApiResponse<List<CatalogService>>> available(@RequestParam(required = false) SubjectType subjectType) {
        return respond(HttpStatus.OK, "OK", serviceCatalog.available(subjectType));
    }

    @PostMapping("/requirements")
    @PreAuthorize("hasRole('COMPANY') or hasRole('ADMIN') or hasRole('SUPER_ADMIN')")
    @Operation(summary = "Evaluate a set of services",
            description = "Execution order, fields, captures and widget screens they need, plus any problems (unknown, unavailable, missing dependency, unsupported country).")
    public ResponseEntity<ApiResponse<ServiceRequirements>> requirements(@Valid @RequestBody RequirementsRequestDto request) {
        return respond(HttpStatus.OK, "OK", serviceCatalog.requirementsFor(
                request.getServices(), request.getSubjectType(), request.getCountry(), true));
    }

    @GetMapping
    @PreAuthorize("hasRole('ADMIN') or hasRole('SUPER_ADMIN')")
    @Operation(summary = "All catalog services, optionally by status")
    public ResponseEntity<ApiResponse<List<CatalogService>>> list(@RequestParam(required = false) ServiceStatus status) {
        return respond(HttpStatus.OK, "OK", adminService.list(status));
    }

    @PostMapping
    @PreAuthorize("hasRole('ADMIN') or hasRole('SUPER_ADMIN')")
    @Operation(summary = "Add a service", description = "Starts IN_DEVELOPMENT, hidden from merchants until made AVAILABLE.")
    public ResponseEntity<ApiResponse<CatalogService>> create(@Valid @RequestBody CatalogServiceRequestDto request) {
        return respond(HttpStatus.CREATED, "Service created", adminService.create(request));
    }

    @PatchMapping("/{code}")
    @PreAuthorize("hasRole('ADMIN') or hasRole('SUPER_ADMIN')")
    @Operation(summary = "Update a service", description = "Omitted fields are kept; the code can't change.")
    public ResponseEntity<ApiResponse<CatalogService>> update(@PathVariable String code,
                                                              @Valid @RequestBody CatalogServiceRequestDto request) {
        return respond(HttpStatus.OK, "Service updated", adminService.update(code, request));
    }

    @PostMapping("/{code}/status")
    @PreAuthorize("hasRole('ADMIN') or hasRole('SUPER_ADMIN')")
    @Operation(summary = "Change a service's status",
            description = "IN_DEVELOPMENT -> AVAILABLE | RETIRED; AVAILABLE -> DEPRECATED; DEPRECATED -> AVAILABLE | RETIRED.")
    public ResponseEntity<ApiResponse<CatalogService>> changeStatus(@PathVariable String code,
                                                                    @Valid @RequestBody ServiceStatusRequestDto request) {
        return respond(HttpStatus.OK, "Status changed", adminService.changeStatus(code, request.getStatus()));
    }

    @DeleteMapping("/{code}")
    @PreAuthorize("hasRole('ADMIN') or hasRole('SUPER_ADMIN')")
    @Operation(summary = "Delete a service still in development", description = "Others are retired instead.")
    public ResponseEntity<ApiResponse<Void>> delete(@PathVariable String code) {
        adminService.delete(code);
        return respond(HttpStatus.OK, "Service deleted", null);
    }
}
