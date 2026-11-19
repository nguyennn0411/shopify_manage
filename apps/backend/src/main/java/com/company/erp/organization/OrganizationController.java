package com.company.erp.organization;

import static com.company.erp.organization.OrganizationModels.*;

import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/organization")
public class OrganizationController {
    private final OrganizationService organization;

    public OrganizationController(OrganizationService organization) { this.organization = organization; }

    @GetMapping("/profile")
    @PreAuthorize("@companyAccess.canAccess(authentication,#companyId,'company.read')")
    CompanyProfile profile(@RequestParam UUID companyId) { return organization.profile(companyId); }

    @PatchMapping("/profile")
    @PreAuthorize("@companyAccess.canAccess(authentication,#companyId,'company.write')")
    CompanyProfile updateProfile(@RequestParam UUID companyId, @Valid @RequestBody CompanyProfileUpdate request,
            Authentication authentication) {
        return organization.updateProfile(companyId, request, authentication.getName());
    }

    @GetMapping("/branches")
    @PreAuthorize("@companyAccess.canAccess(authentication,#companyId,'company.read')")
    List<BranchView> branches(@RequestParam UUID companyId) { return organization.branches(companyId); }

    @PostMapping("/branches")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("@companyAccess.canAccess(authentication,#companyId,'company.write')")
    BranchView createBranch(@RequestParam UUID companyId, @Valid @RequestBody BranchRequest request,
            Authentication authentication) {
        return organization.createBranch(companyId, request, authentication.getName());
    }

    @PutMapping("/branches/{branchId}")
    @PreAuthorize("@companyAccess.canAccess(authentication,#companyId,'company.write')")
    BranchView updateBranch(@RequestParam UUID companyId, @PathVariable UUID branchId,
            @Valid @RequestBody BranchRequest request, Authentication authentication) {
        return organization.updateBranch(companyId, branchId, request, authentication.getName());
    }

    @GetMapping("/departments")
    @PreAuthorize("@companyAccess.canAccess(authentication,#companyId,'company.read')")
    List<DepartmentView> departments(@RequestParam UUID companyId) { return organization.departments(companyId); }

    @PostMapping("/departments")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("@companyAccess.canAccess(authentication,#companyId,'company.write')")
    DepartmentView createDepartment(@RequestParam UUID companyId, @Valid @RequestBody DepartmentRequest request,
            Authentication authentication) {
        return organization.createDepartment(companyId, request, authentication.getName());
    }

    @PutMapping("/departments/{departmentId}")
    @PreAuthorize("@companyAccess.canAccess(authentication,#companyId,'company.write')")
    DepartmentView updateDepartment(@RequestParam UUID companyId, @PathVariable UUID departmentId,
            @Valid @RequestBody DepartmentRequest request, Authentication authentication) {
        return organization.updateDepartment(companyId, departmentId, request, authentication.getName());
    }

    @GetMapping("/positions")
    @PreAuthorize("@companyAccess.canAccess(authentication,#companyId,'company.read')")
    List<PositionView> positions(@RequestParam UUID companyId) { return organization.positions(companyId); }

    @PostMapping("/positions")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("@companyAccess.canAccess(authentication,#companyId,'company.write')")
    PositionView createPosition(@RequestParam UUID companyId, @Valid @RequestBody PositionRequest request,
            Authentication authentication) {
        return organization.createPosition(companyId, request, authentication.getName());
    }

    @PutMapping("/positions/{positionId}")
    @PreAuthorize("@companyAccess.canAccess(authentication,#companyId,'company.write')")
    PositionView updatePosition(@RequestParam UUID companyId, @PathVariable UUID positionId,
            @Valid @RequestBody PositionRequest request, Authentication authentication) {
        return organization.updatePosition(companyId, positionId, request, authentication.getName());
    }

    @GetMapping("/org-chart")
    @PreAuthorize("@companyAccess.canAccess(authentication,#companyId,'company.read')")
    OrgChart orgChart(@RequestParam UUID companyId) { return organization.orgChart(companyId); }

    @GetMapping("/permissions")
    @PreAuthorize("@companyAccess.canAccess(authentication,#companyId,'identity.role.read')")
    List<PermissionView> permissions(@RequestParam UUID companyId) { return organization.permissions(); }

    @GetMapping("/roles")
    @PreAuthorize("@companyAccess.canAccess(authentication,#companyId,'identity.role.read')")
    List<RoleView> roles(@RequestParam UUID companyId) { return organization.roles(companyId); }

    @PostMapping("/roles")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("@companyAccess.canAccess(authentication,#companyId,'identity.role.write')")
    RoleView createRole(@RequestParam UUID companyId, @Valid @RequestBody RoleRequest request,
            Authentication authentication) {
        return organization.createRole(companyId, request, authentication.getName());
    }

    @PutMapping("/roles/{roleId}/permissions")
    @PreAuthorize("@companyAccess.canAccess(authentication,#companyId,'identity.role.write')")
    RoleView replaceRolePermissions(@RequestParam UUID companyId, @PathVariable UUID roleId,
            @Valid @RequestBody RolePermissionsRequest request, Authentication authentication) {
        return organization.replaceRolePermissions(companyId, roleId, request, authentication.getName());
    }

    @GetMapping("/members")
    @PreAuthorize("@companyAccess.canAccess(authentication,#companyId,'identity.account.read')")
    List<MemberView> members(@RequestParam UUID companyId) { return organization.members(companyId); }

    @PostMapping("/members")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("@companyAccess.canAccess(authentication,#companyId,'identity.account.write')")
    MemberView addMember(@RequestParam UUID companyId, @Valid @RequestBody MemberRequest request,
            Authentication authentication) {
        return organization.addMember(companyId, request, authentication.getName());
    }

    @PatchMapping("/members/{subject}/status")
    @PreAuthorize("@companyAccess.canAccess(authentication,#companyId,'identity.account.write')")
    MemberView updateMemberStatus(@RequestParam UUID companyId, @PathVariable String subject,
            @Valid @RequestBody MemberStatusRequest request, Authentication authentication) {
        return organization.updateMemberStatus(companyId, subject, request, authentication.getName());
    }

    @PutMapping("/members/{subject}/access")
    @PreAuthorize("@companyAccess.canAccess(authentication,#companyId,'identity.role.write')")
    MemberView replaceMemberAccess(@RequestParam UUID companyId, @PathVariable String subject,
            @Valid @RequestBody MemberAccessRequest request, Authentication authentication) {
        return organization.replaceMemberAccess(companyId, subject, request, authentication.getName());
    }
}
