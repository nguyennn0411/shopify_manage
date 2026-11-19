package com.company.erp.workplace;

import static com.company.erp.workplace.WorkplaceModels.*;

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

@RestController
@RequestMapping("/api/v1/workplace")
public class WorkplaceController {
    private final WorkplaceService workplace;
    public WorkplaceController(WorkplaceService workplace){this.workplace=workplace;}

    @GetMapping("/summary") @PreAuthorize("@companyAccess.canAccess(authentication,#companyId,'workplace.access')")
    WorkplaceSummary summary(@RequestParam UUID companyId,Authentication auth){return workplace.summary(companyId,auth);}
    @GetMapping("/references/{kind}") @PreAuthorize("@companyAccess.canAccess(authentication,#companyId,'workplace.access')")
    List<Map<String,Object>> references(@RequestParam UUID companyId,@PathVariable String kind,Authentication auth){return workplace.references(companyId,kind,auth);}

    @GetMapping("/folders") @PreAuthorize("@companyAccess.canAccess(authentication,#companyId,'document.read')")
    List<Map<String,Object>> folders(@RequestParam UUID companyId,Authentication auth){return workplace.folders(companyId,auth);}
    @PostMapping("/folders") @ResponseStatus(HttpStatus.CREATED) @PreAuthorize("@companyAccess.canAccess(authentication,#companyId,'document.write')")
    CreatedResource createFolder(@RequestParam UUID companyId,@Valid @RequestBody FolderRequest request,Authentication auth){return workplace.createFolder(companyId,request,auth.getName());}
    @GetMapping("/documents") @PreAuthorize("@companyAccess.canAccess(authentication,#companyId,'document.read')")
    List<Map<String,Object>> documents(@RequestParam UUID companyId,@RequestParam(required=false) String status,Authentication auth){return workplace.documents(companyId,status,auth);}
    @PostMapping("/documents") @ResponseStatus(HttpStatus.CREATED) @PreAuthorize("@companyAccess.canAccess(authentication,#companyId,'document.write')")
    CreatedResource createDocument(@RequestParam UUID companyId,@Valid @RequestBody DocumentRequest request,Authentication auth){return workplace.createDocument(companyId,request,auth.getName());}
    @GetMapping("/documents/{documentId}/versions") @PreAuthorize("@workplaceAccess.canReadDocument(authentication,#companyId,#documentId,'document.read')")
    List<Map<String,Object>> documentVersions(@RequestParam UUID companyId,@PathVariable UUID documentId){return workplace.documentVersions(companyId,documentId);}
    @PostMapping("/documents/{documentId}/versions") @ResponseStatus(HttpStatus.CREATED) @PreAuthorize("@workplaceAccess.canEditDocument(authentication,#companyId,#documentId,'document.write')")
    CreatedResource addDocumentVersion(@RequestParam UUID companyId,@PathVariable UUID documentId,@Valid @RequestBody DocumentVersionRequest request,Authentication auth){return workplace.addDocumentVersion(companyId,documentId,request,auth.getName());}
    @PostMapping("/document-versions/{versionId}/scan") @PreAuthorize("@companyAccess.canAccess(authentication,#companyId,'document.share')")
    CreatedResource markVersionScan(@RequestParam UUID companyId,@PathVariable UUID versionId,@Valid @RequestBody ScanStatusRequest request,Authentication auth){return workplace.markVersionScan(companyId,versionId,request,auth.getName());}
    @PostMapping("/documents/{documentId}/status") @PreAuthorize("@workplaceAccess.canEditDocument(authentication,#companyId,#documentId,'document.write')")
    CreatedResource setDocumentStatus(@RequestParam UUID companyId,@PathVariable UUID documentId,@Valid @RequestBody StatusRequest request,Authentication auth){return workplace.setDocumentStatus(companyId,documentId,request,auth.getName());}
    @PostMapping("/documents/{documentId}/share") @PreAuthorize("@workplaceAccess.canEditDocument(authentication,#companyId,#documentId,'document.share')")
    CreatedResource shareDocument(@RequestParam UUID companyId,@PathVariable UUID documentId,@Valid @RequestBody DocumentShareRequest request,Authentication auth){return workplace.shareDocument(companyId,documentId,request,auth.getName());}

    @GetMapping("/knowledge/spaces") @PreAuthorize("@companyAccess.canAccess(authentication,#companyId,'knowledge.read')")
    List<Map<String,Object>> knowledgeSpaces(@RequestParam UUID companyId,Authentication auth){return workplace.knowledgeSpaces(companyId,auth);}
    @PostMapping("/knowledge/spaces") @ResponseStatus(HttpStatus.CREATED) @PreAuthorize("@companyAccess.canAccess(authentication,#companyId,'knowledge.write')")
    CreatedResource createKnowledgeSpace(@RequestParam UUID companyId,@Valid @RequestBody KnowledgeSpaceRequest request,Authentication auth){return workplace.createKnowledgeSpace(companyId,request,auth.getName());}
    @GetMapping("/knowledge/articles") @PreAuthorize("@companyAccess.canAccess(authentication,#companyId,'knowledge.read')")
    List<Map<String,Object>> knowledgeArticles(@RequestParam UUID companyId,@RequestParam(required=false) String status,Authentication auth){return workplace.knowledgeArticles(companyId,status,auth);}
    @PostMapping("/knowledge/articles") @ResponseStatus(HttpStatus.CREATED) @PreAuthorize("@companyAccess.canAccess(authentication,#companyId,'knowledge.write')")
    CreatedResource createKnowledgeArticle(@RequestParam UUID companyId,@Valid @RequestBody KnowledgeArticleRequest request,Authentication auth){return workplace.createKnowledgeArticle(companyId,request,auth.getName());}
    @PostMapping("/knowledge/articles/{articleId}/status") @PreAuthorize("@companyAccess.canAccess(authentication,#companyId,'knowledge.write')")
    CreatedResource setKnowledgeArticleStatus(@RequestParam UUID companyId,@PathVariable UUID articleId,@Valid @RequestBody StatusRequest request,Authentication auth){return workplace.setKnowledgeArticleStatus(companyId,articleId,request,auth.getName());}

    @GetMapping("/approvals") @PreAuthorize("@companyAccess.canAccess(authentication,#companyId,'approval.read')")
    List<Map<String,Object>> approvals(@RequestParam UUID companyId,@RequestParam(required=false) String status,Authentication auth){return workplace.approvals(companyId,status,auth);}
    @PostMapping("/approvals") @ResponseStatus(HttpStatus.CREATED) @PreAuthorize("@companyAccess.canAccess(authentication,#companyId,'approval.write')")
    CreatedResource createApproval(@RequestParam UUID companyId,@Valid @RequestBody ApprovalRequest request,Authentication auth){return workplace.createApproval(companyId,request,auth.getName());}
    @GetMapping("/approvals/{requestId}/steps") @PreAuthorize("@workplaceAccess.canAccessApproval(authentication,#companyId,#requestId,'approval.read')")
    List<Map<String,Object>> approvalSteps(@RequestParam UUID companyId,@PathVariable UUID requestId){return workplace.approvalSteps(companyId,requestId);}
    @GetMapping("/approvals/{requestId}/comments") @PreAuthorize("@workplaceAccess.canAccessApproval(authentication,#companyId,#requestId,'approval.read')")
    List<Map<String,Object>> approvalComments(@RequestParam UUID companyId,@PathVariable UUID requestId){return workplace.approvalComments(companyId,requestId);}
    @PostMapping("/approvals/{requestId}/submit") @PreAuthorize("@workplaceAccess.canAccessApproval(authentication,#companyId,#requestId,'approval.write')")
    CreatedResource submitApproval(@RequestParam UUID companyId,@PathVariable UUID requestId,Authentication auth){return workplace.submitApproval(companyId,requestId,auth.getName());}
    @PostMapping("/approvals/{requestId}/decision") @PreAuthorize("@workplaceAccess.canDecideApproval(authentication,#companyId,#requestId,'approval.decide')")
    CreatedResource decideApproval(@RequestParam UUID companyId,@PathVariable UUID requestId,@Valid @RequestBody StatusRequest request,Authentication auth){return workplace.decideApproval(companyId,requestId,request,auth.getName());}
    @PostMapping("/approvals/{requestId}/comments") @ResponseStatus(HttpStatus.CREATED) @PreAuthorize("@workplaceAccess.canAccessApproval(authentication,#companyId,#requestId,'approval.read')")
    CreatedResource addApprovalComment(@RequestParam UUID companyId,@PathVariable UUID requestId,@Valid @RequestBody CommentRequest request,Authentication auth){return workplace.addApprovalComment(companyId,requestId,request,auth.getName());}

    @GetMapping("/forms") @PreAuthorize("@companyAccess.canAccess(authentication,#companyId,'form.read')")
    List<Map<String,Object>> forms(@RequestParam UUID companyId,@RequestParam(required=false) String status){return workplace.forms(companyId,status);}
    @PostMapping("/forms") @ResponseStatus(HttpStatus.CREATED) @PreAuthorize("@companyAccess.canAccess(authentication,#companyId,'form.write')")
    CreatedResource createForm(@RequestParam UUID companyId,@Valid @RequestBody FormDefinitionRequest request,Authentication auth){return workplace.createForm(companyId,request,auth.getName());}
    @PostMapping("/forms/{formId}/status") @PreAuthorize("@companyAccess.canAccess(authentication,#companyId,'form.write')")
    CreatedResource setFormStatus(@RequestParam UUID companyId,@PathVariable UUID formId,@Valid @RequestBody StatusRequest request,Authentication auth){return workplace.setFormStatus(companyId,formId,request,auth.getName());}
    @GetMapping("/forms/{formId}/submissions") @PreAuthorize("@companyAccess.canAccess(authentication,#companyId,'form.read')")
    List<Map<String,Object>> formSubmissions(@RequestParam UUID companyId,@PathVariable UUID formId,Authentication auth){return workplace.formSubmissions(companyId,formId,auth);}
    @PostMapping("/forms/{formId}/submissions") @ResponseStatus(HttpStatus.CREATED) @PreAuthorize("@companyAccess.canAccess(authentication,#companyId,'form.submit')")
    CreatedResource submitForm(@RequestParam UUID companyId,@PathVariable UUID formId,@Valid @RequestBody FormSubmissionRequest request,Authentication auth){return workplace.submitForm(companyId,formId,request,auth.getName());}

    @GetMapping("/signatures") @PreAuthorize("@companyAccess.canAccess(authentication,#companyId,'signature.read')")
    List<Map<String,Object>> signatures(@RequestParam UUID companyId,@RequestParam(required=false) String status,Authentication auth){return workplace.signatureRequests(companyId,status,auth);}
    @PostMapping("/signatures") @ResponseStatus(HttpStatus.CREATED) @PreAuthorize("@workplaceAccess.canReadDocument(authentication,#companyId,#request.documentId(),'signature.write')")
    CreatedResource createSignature(@RequestParam UUID companyId,@Valid @RequestBody SignatureRequest request,Authentication auth){return workplace.createSignature(companyId,request,auth.getName());}
    @GetMapping("/signatures/{requestId}/recipients") @PreAuthorize("@workplaceAccess.canAccessSignature(authentication,#companyId,#requestId,'signature.read')")
    List<Map<String,Object>> signatureRecipients(@RequestParam UUID companyId,@PathVariable UUID requestId){return workplace.signatureRecipients(companyId,requestId);}
    @PostMapping("/signatures/{requestId}/send") @PreAuthorize("@workplaceAccess.canAccessSignature(authentication,#companyId,#requestId,'signature.write')")
    CreatedResource sendSignature(@RequestParam UUID companyId,@PathVariable UUID requestId,Authentication auth){return workplace.sendSignature(companyId,requestId,auth.getName());}
    @PostMapping("/signatures/{requestId}/decision") @PreAuthorize("@workplaceAccess.canSign(authentication,#companyId,#requestId,'signature.sign')")
    CreatedResource decideSignature(@RequestParam UUID companyId,@PathVariable UUID requestId,@Valid @RequestBody SignatureDecisionRequest request,Authentication auth){return workplace.decideSignature(companyId,requestId,request,auth.getName());}

    @GetMapping("/helpdesk") @PreAuthorize("@companyAccess.canAccess(authentication,#companyId,'helpdesk.read')")
    List<Map<String,Object>> tickets(@RequestParam UUID companyId,@RequestParam(required=false) String status,Authentication auth){return workplace.tickets(companyId,status,auth);}
    @PostMapping("/helpdesk") @ResponseStatus(HttpStatus.CREATED) @PreAuthorize("@companyAccess.canAccess(authentication,#companyId,'helpdesk.write')")
    CreatedResource createTicket(@RequestParam UUID companyId,@Valid @RequestBody HelpdeskTicketRequest request,Authentication auth){return workplace.createTicket(companyId,request,auth.getName());}
    @PostMapping("/helpdesk/{ticketId}/status") @PreAuthorize("@companyAccess.canAccess(authentication,#companyId,'helpdesk.manage')")
    CreatedResource updateTicket(@RequestParam UUID companyId,@PathVariable UUID ticketId,@Valid @RequestBody HelpdeskUpdateRequest request,Authentication auth){return workplace.updateTicket(companyId,ticketId,request,auth.getName());}
    @GetMapping("/helpdesk/{ticketId}/comments") @PreAuthorize("@workplaceAccess.canAccessTicket(authentication,#companyId,#ticketId,'helpdesk.read')")
    List<Map<String,Object>> ticketComments(@RequestParam UUID companyId,@PathVariable UUID ticketId){return workplace.ticketComments(companyId,ticketId,false);}
    @GetMapping("/helpdesk/{ticketId}/internal-comments") @PreAuthorize("@companyAccess.canAccess(authentication,#companyId,'helpdesk.manage')")
    List<Map<String,Object>> internalTicketComments(@RequestParam UUID companyId,@PathVariable UUID ticketId){return workplace.ticketComments(companyId,ticketId,true);}
    @PostMapping("/helpdesk/{ticketId}/comments") @ResponseStatus(HttpStatus.CREATED) @PreAuthorize("@workplaceAccess.canAccessTicket(authentication,#companyId,#ticketId,'helpdesk.write')")
    CreatedResource addTicketComment(@RequestParam UUID companyId,@PathVariable UUID ticketId,@Valid @RequestBody HelpdeskCommentRequest request,Authentication auth){return workplace.addTicketComment(companyId,ticketId,request,auth.getName(),false);}
    @PostMapping("/helpdesk/{ticketId}/internal-comments") @ResponseStatus(HttpStatus.CREATED) @PreAuthorize("@companyAccess.canAccess(authentication,#companyId,'helpdesk.manage')")
    CreatedResource addInternalTicketComment(@RequestParam UUID companyId,@PathVariable UUID ticketId,@Valid @RequestBody HelpdeskCommentRequest request,Authentication auth){return workplace.addTicketComment(companyId,ticketId,new HelpdeskCommentRequest(request.content(),true),auth.getName(),true);}

    @GetMapping("/channels") @PreAuthorize("@companyAccess.canAccess(authentication,#companyId,'communication.read')")
    List<Map<String,Object>> channels(@RequestParam UUID companyId,Authentication auth){return workplace.channels(companyId,auth);}
    @PostMapping("/channels") @ResponseStatus(HttpStatus.CREATED) @PreAuthorize("@companyAccess.canAccess(authentication,#companyId,'communication.moderate')")
    CreatedResource createChannel(@RequestParam UUID companyId,@Valid @RequestBody ChannelRequest request,Authentication auth){return workplace.createChannel(companyId,request,auth.getName());}
    @GetMapping("/channels/{channelId}/messages") @PreAuthorize("@workplaceAccess.canAccessChannel(authentication,#companyId,#channelId,'communication.read')")
    List<Map<String,Object>> messages(@RequestParam UUID companyId,@PathVariable UUID channelId){return workplace.messages(companyId,channelId);}
    @PostMapping("/channels/{channelId}/messages") @ResponseStatus(HttpStatus.CREATED) @PreAuthorize("@workplaceAccess.canAccessChannel(authentication,#companyId,#channelId,'communication.write')")
    CreatedResource createMessage(@RequestParam UUID companyId,@PathVariable UUID channelId,@Valid @RequestBody MessageRequest request,Authentication auth){return workplace.createMessage(companyId,channelId,request,auth.getName());}

    @GetMapping("/legal") @PreAuthorize("@companyAccess.canAccess(authentication,#companyId,'legal.read')")
    List<Map<String,Object>> legalRecords(@RequestParam UUID companyId,@RequestParam(required=false) String status){return workplace.legalRecords(companyId,status);}
    @PostMapping("/legal") @ResponseStatus(HttpStatus.CREATED) @PreAuthorize("@companyAccess.canAccess(authentication,#companyId,'legal.write')")
    CreatedResource createLegalRecord(@RequestParam UUID companyId,@Valid @RequestBody LegalRecordRequest request,Authentication auth){return workplace.createLegalRecord(companyId,request,auth.getName());}
    @GetMapping("/legal/{legalId}/obligations") @PreAuthorize("@companyAccess.canAccess(authentication,#companyId,'legal.read')")
    List<Map<String,Object>> obligations(@RequestParam UUID companyId,@PathVariable UUID legalId){return workplace.obligations(companyId,legalId);}
    @PostMapping("/legal/{legalId}/obligations") @ResponseStatus(HttpStatus.CREATED) @PreAuthorize("@companyAccess.canAccess(authentication,#companyId,'legal.write')")
    CreatedResource createObligation(@RequestParam UUID companyId,@PathVariable UUID legalId,@Valid @RequestBody LegalObligationRequest request,Authentication auth){return workplace.createObligation(companyId,legalId,request,auth.getName());}
    @PostMapping("/legal/obligations/{obligationId}/status") @PreAuthorize("@companyAccess.canAccess(authentication,#companyId,'legal.write')")
    CreatedResource setObligationStatus(@RequestParam UUID companyId,@PathVariable UUID obligationId,@Valid @RequestBody StatusRequest request,Authentication auth){return workplace.setObligationStatus(companyId,obligationId,request,auth.getName());}
}
