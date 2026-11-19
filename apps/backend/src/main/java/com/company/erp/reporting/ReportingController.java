package com.company.erp.reporting;

import static com.company.erp.reporting.ReportingModels.*;

import jakarta.validation.Valid;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/reporting")
public class ReportingController {
    private final ReportingService service;
    public ReportingController(ReportingService service){this.service=service;}

    @GetMapping("/summary") @PreAuthorize("@reportingAccess.canAccess(authentication,#companyId,'reporting.read')")
    ExecutiveSummary summary(@RequestParam UUID companyId,Authentication authentication){return service.summary(companyId,authentication);}
    @GetMapping("/trends") @PreAuthorize("@reportingAccess.canAccess(authentication,#companyId,'reporting.read')")
    List<TrendPoint> trends(@RequestParam UUID companyId,Authentication authentication){return service.trends(companyId,authentication);}
    @GetMapping("/project-health") @PreAuthorize("@reportingAccess.canAccess(authentication,#companyId,'reporting.read')")
    List<Map<String,Object>> projectHealth(@RequestParam UUID companyId,Authentication authentication){return service.projectHealth(companyId,authentication);}
    @GetMapping("/objectives") @PreAuthorize("@reportingAccess.canAccess(authentication,#companyId,'strategy.read')")
    List<Map<String,Object>> objectives(@RequestParam UUID companyId,Authentication authentication){return service.objectives(companyId,authentication);}
    @PostMapping("/objectives") @ResponseStatus(HttpStatus.CREATED) @PreAuthorize("@reportingAccess.canAccess(authentication,#companyId,'strategy.write')")
    CreatedResource createObjective(@RequestParam UUID companyId,@Valid @RequestBody ObjectiveRequest request,Authentication authentication){return service.createObjective(companyId,request,authentication);}
    @GetMapping("/objectives/{id}/key-results") @PreAuthorize("@reportingAccess.canAccess(authentication,#companyId,'strategy.read')")
    List<Map<String,Object>> keyResults(@RequestParam UUID companyId,@PathVariable UUID id,Authentication authentication){return service.keyResults(companyId,id,authentication);}
    @PostMapping("/objectives/{id}/status") @PreAuthorize("@reportingAccess.canAccess(authentication,#companyId,'strategy.write')")
    CreatedResource setStatus(@RequestParam UUID companyId,@PathVariable UUID id,@Valid @RequestBody StatusRequest request,Authentication authentication){return service.setObjectiveStatus(companyId,id,request,authentication);}
    @PostMapping("/key-results/{id}/progress") @PreAuthorize("@reportingAccess.canAccess(authentication,#companyId,'strategy.write')")
    CreatedResource updateProgress(@RequestParam UUID companyId,@PathVariable UUID id,@Valid @RequestBody ProgressRequest request,Authentication authentication){return service.updateProgress(companyId,id,request,authentication);}
    @GetMapping("/snapshots") @PreAuthorize("@reportingAccess.canAccess(authentication,#companyId,'reporting.read')")
    List<Map<String,Object>> snapshots(@RequestParam UUID companyId,Authentication authentication){return service.snapshots(companyId,authentication);}
    @GetMapping("/snapshots/{id}") @PreAuthorize("@reportingAccess.canAccess(authentication,#companyId,'reporting.read')")
    Map<String,Object> snapshot(@RequestParam UUID companyId,@PathVariable UUID id,Authentication authentication){return service.snapshot(companyId,id,authentication);}
    @PostMapping("/snapshots") @ResponseStatus(HttpStatus.CREATED) @PreAuthorize("@reportingAccess.canAccess(authentication,#companyId,'reporting.manage')")
    CreatedResource createSnapshot(@RequestParam UUID companyId,@Valid @RequestBody SnapshotRequest request,Authentication authentication){return service.createSnapshot(companyId,request,authentication);}
    @GetMapping(value="/export",produces="text/csv") @PreAuthorize("@reportingAccess.canAccess(authentication,#companyId,'reporting.export')")
    ResponseEntity<String> export(@RequestParam UUID companyId,Authentication authentication){return ResponseEntity.ok().contentType(MediaType.parseMediaType("text/csv;charset=UTF-8")).header(HttpHeaders.CONTENT_DISPOSITION,"attachment; filename=executive-report.csv").body("\uFEFF"+service.exportCsv(companyId,authentication));}
}
