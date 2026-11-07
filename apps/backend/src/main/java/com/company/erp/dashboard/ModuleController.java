package com.company.erp.dashboard;

import java.util.Map;
import java.util.UUID;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/modules")
public class ModuleController {
    private final ModuleSummaryService modules;

    public ModuleController(ModuleSummaryService modules) { this.modules = modules; }

    @GetMapping("/summary")
    @PreAuthorize("@companyAccess.canAccess(authentication, #companyId, 'company.read')")
    Map<String, Long> summary(@RequestParam UUID companyId) { return modules.counts(companyId); }
}
