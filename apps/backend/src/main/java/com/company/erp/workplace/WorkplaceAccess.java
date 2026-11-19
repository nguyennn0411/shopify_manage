package com.company.erp.workplace;

import com.company.erp.company.CompanyAccess;
import java.util.Map;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Component;

@Component("workplaceAccess")
public class WorkplaceAccess {
    private final CompanyAccess companyAccess;
    private final JdbcClient jdbc;
    public WorkplaceAccess(CompanyAccess companyAccess,JdbcClient jdbc){this.companyAccess=companyAccess;this.jdbc=jdbc;}

    public boolean canAccess(Authentication auth,UUID companyId,String permission){return companyAccess.canAccess(auth,companyId,permission);}

    public boolean canReadDocument(Authentication auth,UUID companyId,UUID documentId,String permission){
        if(documentId==null||!companyAccess.canAccess(auth,companyId,permission))return false;
        Map<String,Object> d=document(companyId,documentId);if(d==null)return false;
        if(hasGlobal(auth,companyId,permission)||auth.getName().equals(text(d.get("owner_subject"))))return true;
        if(jdbc.sql("select count(*) from workplace_document_access where company_id=:companyId and document_id=:id and subject=:subject")
            .param("companyId",companyId.toString()).param("id",documentId.toString()).param("subject",auth.getName()).query(Integer.class).single()>0)return true;
        return scope(auth,companyId,text(d.get("scope_type")),id(d.get("scope_id")));
    }

    public boolean canEditDocument(Authentication auth,UUID companyId,UUID documentId,String permission){
        if(documentId==null||!companyAccess.canAccess(auth,companyId,permission))return false;
        Map<String,Object>d=document(companyId,documentId);if(d==null)return false;
        if(hasGlobal(auth,companyId,permission)||auth.getName().equals(text(d.get("owner_subject"))))return true;
        return jdbc.sql("select count(*) from workplace_document_access where company_id=:companyId and document_id=:id and subject=:subject and access_level='EDIT'")
            .param("companyId",companyId.toString()).param("id",documentId.toString()).param("subject",auth.getName()).query(Integer.class).single()>0;
    }

    public boolean canAccessApproval(Authentication auth,UUID companyId,UUID requestId,String permission){
        if(requestId==null||!companyAccess.canAccess(auth,companyId,permission))return false;if(hasGlobal(auth,companyId,permission))return exists("workplace_approval_requests",companyId,requestId);
        return jdbc.sql("select count(*) from workplace_approval_requests r where r.company_id=:companyId and r.id=:id and (r.requester_subject=:subject or exists(select 1 from workplace_approval_steps s where s.approval_request_id=r.id and s.approver_subject=:subject))")
            .param("companyId",companyId.toString()).param("id",requestId.toString()).param("subject",auth.getName()).query(Integer.class).single()>0;
    }

    public boolean canDecideApproval(Authentication auth,UUID companyId,UUID requestId,String permission){
        if(requestId==null||!companyAccess.canAccess(auth,companyId,permission))return false;if(hasGlobal(auth,companyId,permission))return exists("workplace_approval_requests",companyId,requestId);
        return jdbc.sql("select count(*) from workplace_approval_steps where company_id=:companyId and approval_request_id=:id and approver_subject=:subject and status='PENDING'")
            .param("companyId",companyId.toString()).param("id",requestId.toString()).param("subject",auth.getName()).query(Integer.class).single()>0;
    }

    public boolean canAccessSignature(Authentication auth,UUID companyId,UUID requestId,String permission){
        if(requestId==null||!companyAccess.canAccess(auth,companyId,permission))return false;if(hasGlobal(auth,companyId,permission))return exists("workplace_signature_requests",companyId,requestId);
        return jdbc.sql("select count(*) from workplace_signature_requests r where r.company_id=:companyId and r.id=:id and (r.requested_by_subject=:subject or exists(select 1 from workplace_signature_recipients s where s.signature_request_id=r.id and s.signer_subject=:subject))")
            .param("companyId",companyId.toString()).param("id",requestId.toString()).param("subject",auth.getName()).query(Integer.class).single()>0;
    }

    public boolean canSign(Authentication auth,UUID companyId,UUID requestId,String permission){
        if(requestId==null||!companyAccess.canAccess(auth,companyId,permission))return false;
        return jdbc.sql("select count(*) from workplace_signature_recipients where company_id=:companyId and signature_request_id=:id and signer_subject=:subject and status='PENDING'")
            .param("companyId",companyId.toString()).param("id",requestId.toString()).param("subject",auth.getName()).query(Integer.class).single()>0;
    }

    public boolean canAccessTicket(Authentication auth,UUID companyId,UUID ticketId,String permission){
        if(ticketId==null||!companyAccess.canAccess(auth,companyId,permission))return false;if(hasGlobal(auth,companyId,permission))return exists("workplace_helpdesk_tickets",companyId,ticketId);
        return jdbc.sql("select count(*) from workplace_helpdesk_tickets t where t.company_id=:companyId and t.id=:id and (t.requester_subject=:subject or t.assigned_subject=:subject or (t.department_id is not null and exists(select 1 from company_membership_scopes s where s.company_id=t.company_id and s.keycloak_subject=:subject and s.scope_type='DEPARTMENT' and s.scope_id=t.department_id)))")
            .param("companyId",companyId.toString()).param("id",ticketId.toString()).param("subject",auth.getName()).query(Integer.class).single()>0;
    }

    public boolean canAccessChannel(Authentication auth,UUID companyId,UUID channelId,String permission){
        if(channelId==null||!companyAccess.canAccess(auth,companyId,permission))return false;var found=jdbc.sql("select channel_type,department_id,project_id from workplace_channels where company_id=:companyId and id=:id and archived=false")
            .param("companyId",companyId.toString()).param("id",channelId.toString()).query().listOfRows();Map<String,Object>c=found.isEmpty()?null:found.getFirst();if(c==null)return false;if(hasGlobal(auth,companyId,permission)||"COMPANY".equals(text(c.get("channel_type"))))return true;UUID scopeId="DEPARTMENT".equals(text(c.get("channel_type")))?id(c.get("department_id")):id(c.get("project_id"));return scope(auth,companyId,text(c.get("channel_type")),scopeId);
    }

    public boolean hasGlobal(Authentication auth,UUID companyId,String permission){return auth.getAuthorities().stream().anyMatch(a->a.getAuthority().equals(permission)||a.getAuthority().equals("super-admin")||a.getAuthority().equals("ROLE_SUPER_ADMIN"))||companyAccess.hasScope(auth,companyId,"COMPANY",companyId);}
    public boolean canAccessScope(Authentication auth,UUID companyId,String scopeType,UUID scopeId){return scope(auth,companyId,scopeType,scopeId);}
    private boolean scope(Authentication auth,UUID companyId,String type,UUID scopeId){if("COMPANY".equals(type))return true;if("PRIVATE".equals(type)||scopeId==null)return false;return companyAccess.hasScope(auth,companyId,type,scopeId);}
    private Map<String,Object> document(UUID companyId,UUID id){var found=jdbc.sql("select owner_subject,scope_type,scope_id from workplace_documents where company_id=:companyId and id=:id and status<>'ARCHIVED'").param("companyId",companyId.toString()).param("id",id.toString()).query().listOfRows();return found.isEmpty()?null:found.getFirst();}
    private boolean exists(String table,UUID companyId,UUID id){return jdbc.sql("select count(*) from "+table+" where company_id=:companyId and id=:id").param("companyId",companyId.toString()).param("id",id.toString()).query(Integer.class).single()>0;}
    private static String text(Object value){return value==null?"":value.toString();}private static UUID id(Object value){return value==null?null:UUID.fromString(value.toString().trim());}
}
