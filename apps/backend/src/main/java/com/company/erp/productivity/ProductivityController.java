package com.company.erp.productivity;

import static com.company.erp.productivity.ProductivityModels.*;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@Validated @RestController @RequestMapping("/api/v1/productivity")
public class ProductivityController {
    private final ProductivityService service;public ProductivityController(ProductivityService service){this.service=service;}
    @GetMapping("/summary") @PreAuthorize("@companyAccess.canAccess(authentication,#companyId,'platform.search')") ProductivitySummary summary(@RequestParam UUID companyId,Authentication auth){return service.summary(companyId,auth);}
    @GetMapping("/search") @PreAuthorize("@companyAccess.canAccess(authentication,#companyId,'platform.search')") List<SearchResult> search(@RequestParam UUID companyId,@RequestParam @Size(min=2,max=120) String q,@RequestParam(required=false) @Min(1) @Max(50) Integer limit,Authentication auth){return service.search(companyId,q,limit,auth);}
    @GetMapping("/inbox") @PreAuthorize("@companyAccess.canAccess(authentication,#companyId,'platform.search')") List<Map<String,Object>> inbox(@RequestParam UUID companyId,Authentication auth){return service.inbox(companyId,auth);}
    @GetMapping("/notifications") @PreAuthorize("@companyAccess.canAccess(authentication,#companyId,'notification.read')") List<Map<String,Object>> notifications(@RequestParam UUID companyId,@RequestParam(defaultValue="false") boolean includeArchived,Authentication auth){return service.notifications(companyId,auth,includeArchived);}
    @PostMapping("/notifications") @ResponseStatus(HttpStatus.CREATED) @PreAuthorize("@companyAccess.canAccess(authentication,#companyId,'notification.write') or @companyAccess.canAccess(authentication,#companyId,'notification.manage')") CreatedResource createNotification(@RequestParam UUID companyId,@Valid @RequestBody NotificationRequest request,Authentication auth){return service.createNotification(companyId,request,auth);}
    @PostMapping("/notifications/{id}/{action}") @PreAuthorize("@companyAccess.canAccess(authentication,#companyId,'notification.read')") CreatedResource markNotification(@RequestParam UUID companyId,@PathVariable UUID id,@PathVariable String action,Authentication auth){return service.markNotification(companyId,id,action.toUpperCase(),auth);}
    @GetMapping("/notification-preferences") @PreAuthorize("@companyAccess.canAccess(authentication,#companyId,'notification.read')") List<Map<String,Object>> preferences(@RequestParam UUID companyId,Authentication auth){return service.preferences(companyId,auth);}
    @PostMapping("/notification-preferences") @PreAuthorize("@companyAccess.canAccess(authentication,#companyId,'notification.read')") CreatedResource savePreference(@RequestParam UUID companyId,@Valid @RequestBody NotificationPreferenceRequest request,Authentication auth){return service.savePreference(companyId,request,auth);}
    @GetMapping("/saved-views") @PreAuthorize("@companyAccess.canAccess(authentication,#companyId,'saved-view.read')") List<Map<String,Object>> savedViews(@RequestParam UUID companyId,@RequestParam(required=false) String moduleKey,Authentication auth){return service.savedViews(companyId,moduleKey,auth);}
    @PostMapping("/saved-views") @ResponseStatus(HttpStatus.CREATED) @PreAuthorize("@companyAccess.canAccess(authentication,#companyId,'saved-view.write')") CreatedResource createSavedView(@RequestParam UUID companyId,@Valid @RequestBody SavedViewRequest request,Authentication auth){return service.createSavedView(companyId,request,auth);}
    @DeleteMapping("/saved-views/{id}") @ResponseStatus(HttpStatus.NO_CONTENT) @PreAuthorize("@companyAccess.canAccess(authentication,#companyId,'saved-view.write')") void deleteSavedView(@RequestParam UUID companyId,@PathVariable UUID id,Authentication auth){service.deleteSavedView(companyId,id,auth);}
    @GetMapping("/audit") @PreAuthorize("@companyAccess.canAccess(authentication,#companyId,'audit.read')") List<Map<String,Object>> auditLogs(@RequestParam UUID companyId,@RequestParam(required=false) String action,@RequestParam(required=false) String resourceType,@RequestParam(required=false) String actor,@RequestParam(required=false) Integer limit){return service.auditLogs(companyId,new AuditQuery(action,resourceType,actor,limit));}
}
