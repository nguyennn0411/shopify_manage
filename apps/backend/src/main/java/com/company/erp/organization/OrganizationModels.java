package com.company.erp.organization;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.util.List;
import java.util.UUID;

public final class OrganizationModels {
    private OrganizationModels() {}

    public record CompanyProfile(UUID id, String name, String code, String legalName, String brandName,
        String taxCode, String registrationNumber, String email, String phone, String website, String address,
        String logoObjectKey, String bankName, String bankAccountName, String bankAccountNumber,
        String baseCurrency, String timezone, String status) {}

    public record CompanyProfileUpdate(
        @Size(max = 180) String name, @Size(max = 255) String legalName, @Size(max = 180) String brandName,
        @Size(max = 80) String taxCode, @Size(max = 100) String registrationNumber,
        @Email @Size(max = 254) String email, @Size(max = 80) String phone,
        @Size(max = 500) String website, @Size(max = 4000) String address,
        @Size(max = 500) String logoObjectKey, @Size(max = 180) String bankName,
        @Size(max = 180) String bankAccountName, @Size(max = 120) String bankAccountNumber,
        @Pattern(regexp = "[A-Z]{3}") String baseCurrency, @Size(max = 80) String timezone) {}

    public record BranchRequest(@NotBlank @Size(max = 50) String code,
        @NotBlank @Size(max = 180) String name, @Size(max = 4000) String address,
        @Size(max = 80) String timezone, @Size(max = 80) String phone,
        @Email @Size(max = 254) String email, UUID managerEmployeeId,
        @Pattern(regexp = "ACTIVE|INACTIVE") String status) {}

    public record BranchView(UUID id, String code, String name, String address, String timezone,
        String phone, String email, UUID managerEmployeeId, String managerName, String status) {}

    public record DepartmentRequest(@NotBlank @Size(max = 50) String code,
        @NotBlank @Size(max = 180) String name, @NotBlank @Size(max = 40) String functionType,
        UUID branchId, UUID parentDepartmentId, UUID managerEmployeeId,
        @Size(max = 4000) String description, @Pattern(regexp = "ACTIVE|INACTIVE") String status) {}

    public record DepartmentView(UUID id, String code, String name, String functionType,
        UUID branchId, String branchName, UUID parentDepartmentId, String parentDepartmentName,
        UUID managerEmployeeId, String managerName, String description, String status) {}

    public record PositionRequest(@NotBlank @Size(max = 50) String code,
        @NotBlank @Size(max = 180) String title, UUID departmentId,
        @Size(max = 80) String levelName, UUID reportsToPositionId,
        @Size(max = 4000) String description, @Pattern(regexp = "ACTIVE|INACTIVE") String status) {}

    public record PositionView(UUID id, String code, String title, UUID departmentId,
        String departmentName, String levelName, UUID reportsToPositionId,
        String reportsToTitle, String description, String status) {}

    public record OrgEmployee(UUID id, String employeeCode, String fullName, UUID departmentId,
        UUID managerEmployeeId, UUID positionId, String jobTitle, String employmentStatus) {}

    public record OrgChart(List<BranchView> branches, List<DepartmentView> departments,
        List<PositionView> positions, List<OrgEmployee> employees) {}

    public record PermissionView(String code, String module, String description, boolean sensitive) {}

    public record RoleRequest(@NotBlank @Size(max = 60) String code,
        @NotBlank @Size(max = 120) String name, @Size(max = 500) String description) {}

    public record RoleView(UUID id, String code, String name, String description,
        boolean systemRole, boolean active, List<String> permissions) {}

    public record RolePermissionsRequest(@NotEmpty List<@NotBlank @Size(max = 120) String> permissions) {}

    public record MemberRequest(@NotBlank @Size(max = 100) String keycloakSubject,
        @NotBlank @Email @Size(max = 254) String email, @NotBlank @Size(max = 180) String displayName,
        @Pattern(regexp = "INVITED|ACTIVE") String status) {}

    public record MemberStatusRequest(@NotBlank @Pattern(regexp = "INVITED|ACTIVE|DISABLED") String status) {}

    public record ScopeGrant(@NotBlank @Pattern(regexp = "SELF|TEAM|DEPARTMENT|BRANCH|PROJECT|COMPANY")
        String scopeType, UUID scopeId) {}

    public record MemberAccessRequest(List<UUID> roleIds, @Valid List<ScopeGrant> scopes) {}

    public record ScopeView(String scopeType, UUID scopeId) {}

    public record MemberView(String keycloakSubject, String email, String displayName, String status,
        List<UUID> roleIds, List<String> roleNames, List<ScopeView> scopes) {}
}
