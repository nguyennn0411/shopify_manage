package com.company.erp.automation;

import static com.company.erp.automation.AutomationModels.*;

import jakarta.validation.Valid;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.http.HttpStatus;
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

@RestController @RequestMapping("/api/v1/automation")
public class AutomationController {
    private final AutomationService service;public AutomationController(AutomationService service){this.service=service;}
    @GetMapping("/summary") @PreAuthorize("@companyAccess.canAccess(authentication,#companyId,'automation.access')") AutomationSummary summary(@RequestParam UUID companyId){return service.summary(companyId);}
    @GetMapping("/connections") @PreAuthorize("@companyAccess.canAccess(authentication,#companyId,'integration.read')") List<Map<String,Object>> connections(@RequestParam UUID companyId){return service.connections(companyId);}
    @PostMapping("/connections") @ResponseStatus(HttpStatus.CREATED) @PreAuthorize("@companyAccess.canAccess(authentication,#companyId,'integration.write')") CreatedResource createConnection(@RequestParam UUID companyId,@Valid @RequestBody ConnectionRequest request,Authentication auth){return service.createConnection(companyId,request,auth.getName());}
    @PostMapping("/connections/{id}/status") @PreAuthorize("@companyAccess.canAccess(authentication,#companyId,'integration.write')") CreatedResource setConnectionStatus(@RequestParam UUID companyId,@PathVariable UUID id,@Valid @RequestBody StatusRequest request,Authentication auth){return service.setConnectionStatus(companyId,id,request,auth.getName());}
    @GetMapping("/webhooks") @PreAuthorize("@companyAccess.canAccess(authentication,#companyId,'webhook.read')") List<Map<String,Object>> endpoints(@RequestParam UUID companyId){return service.endpoints(companyId);}
    @PostMapping("/webhooks") @ResponseStatus(HttpStatus.CREATED) @PreAuthorize("@companyAccess.canAccess(authentication,#companyId,'webhook.write')") CreatedResource createEndpoint(@RequestParam UUID companyId,@Valid @RequestBody WebhookEndpointRequest request,Authentication auth){return service.createEndpoint(companyId,request,auth.getName());}
    @GetMapping("/webhook-deliveries") @PreAuthorize("@companyAccess.canAccess(authentication,#companyId,'webhook.read')") List<Map<String,Object>> deliveries(@RequestParam UUID companyId,@RequestParam(required=false) String status){return service.deliveries(companyId,status);}
    @PostMapping("/webhook-deliveries/{id}/retry") @PreAuthorize("@companyAccess.canAccess(authentication,#companyId,'webhook.write')") CreatedResource retryDelivery(@RequestParam UUID companyId,@PathVariable UUID id,Authentication auth){return service.retryDelivery(companyId,id,auth.getName());}
    @GetMapping("/workflows") @PreAuthorize("@companyAccess.canAccess(authentication,#companyId,'automation.read')") List<Map<String,Object>> workflows(@RequestParam UUID companyId){return service.workflows(companyId);}
    @PostMapping("/workflows") @ResponseStatus(HttpStatus.CREATED) @PreAuthorize("@companyAccess.canAccess(authentication,#companyId,'automation.write')") CreatedResource createWorkflow(@RequestParam UUID companyId,@Valid @RequestBody WorkflowRequest request,Authentication auth){return service.createWorkflow(companyId,request,auth.getName());}
    @GetMapping("/workflows/{id}/steps") @PreAuthorize("@companyAccess.canAccess(authentication,#companyId,'automation.read')") List<Map<String,Object>> steps(@RequestParam UUID companyId,@PathVariable UUID id){return service.steps(companyId,id);}
    @PostMapping("/workflows/{id}/status") @PreAuthorize("@companyAccess.canAccess(authentication,#companyId,'automation.write')") CreatedResource setWorkflowStatus(@RequestParam UUID companyId,@PathVariable UUID id,@Valid @RequestBody StatusRequest request,Authentication auth){return service.setWorkflowStatus(companyId,id,request,auth.getName());}
    @PostMapping("/workflows/{id}/run") @PreAuthorize("@companyAccess.canAccess(authentication,#companyId,'automation.run')") CreatedResource run(@RequestParam UUID companyId,@PathVariable UUID id,@RequestBody(required=false) RunRequest request,Authentication auth){return service.run(companyId,id,request==null?new RunRequest("{}"):request,auth.getName());}
    @PostMapping("/events/{eventType}") @PreAuthorize("@companyAccess.canAccess(authentication,#companyId,'automation.run')") List<CreatedResource> emitEvent(@RequestParam UUID companyId,@PathVariable String eventType,@RequestBody(required=false) RunRequest request,Authentication auth){return service.emitEvent(companyId,eventType,request==null?new RunRequest("{}"):request,auth.getName());}
    @GetMapping("/runs") @PreAuthorize("@companyAccess.canAccess(authentication,#companyId,'automation.read')") List<Map<String,Object>> runs(@RequestParam UUID companyId,@RequestParam(required=false) UUID workflowId){return service.runs(companyId,workflowId);}
    @GetMapping("/runs/{id}/steps") @PreAuthorize("@companyAccess.canAccess(authentication,#companyId,'automation.read')") List<Map<String,Object>> runSteps(@RequestParam UUID companyId,@PathVariable UUID id){return service.runSteps(companyId,id);}
}
