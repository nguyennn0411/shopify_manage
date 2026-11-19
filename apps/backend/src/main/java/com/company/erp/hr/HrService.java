package com.company.erp.hr;

import com.company.erp.audit.AuditService;
import com.company.erp.hr.HrModels.EmployeeCreateRequest;
import com.company.erp.hr.HrModels.EmployeeView;
import com.company.erp.hr.HrModels.HrSummary;
import com.company.erp.hr.HrModels.PayslipView;
import com.company.erp.hr.HrModels.PrivateProfileRequest;
import com.company.erp.hr.HrModels.PrivateProfileView;
import com.company.erp.hr.HrModels.*;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.security.core.Authentication;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class HrService {
    private final JdbcClient jdbc;
    private final NamedParameterJdbcTemplate namedJdbc;
    private final SensitiveDataCipher cipher;
    private final AuditService audit;

    public HrService(JdbcClient jdbc, NamedParameterJdbcTemplate namedJdbc, SensitiveDataCipher cipher, AuditService audit) {
        this.jdbc = jdbc;
        this.namedJdbc = namedJdbc;
        this.cipher = cipher;
        this.audit = audit;
    }

    public HrSummary summary(UUID companyId, Authentication authentication) {
        return new HrSummary(
            allowed(authentication, "hr.employee.read.basic") ? count("employees", "employment_status in ('ACTIVE','PROBATION')", companyId) : null,
            allowed(authentication, "hr.recruitment.read") ? count("recruitment_positions", "status='OPEN'", companyId) : null,
            allowed(authentication, "hr.recruitment.read") ? count("recruitment_candidates", "status='ACTIVE'", companyId) : null,
            allowed(authentication, "hr.onboarding.read") ? count("onboarding_cases", "status not in ('COMPLETED','CANCELLED')", companyId) : null,
            allowed(authentication, "hr.attendance.read") ? count("attendance_entries", "work_date=current_date", companyId) : null,
            allowed(authentication, "hr.leave.read") ? count("leave_requests", "status='PENDING'", companyId) : null,
            allowed(authentication, "hr.payroll.read.all") ? count("payroll_runs", "status in ('DRAFT','REVIEW')", companyId) : null,
            allowed(authentication, "hr.performance.read") ? count("performance_reviews", "status not in ('COMPLETED','CANCELLED')", companyId) : null,
            allowed(authentication, "hr.training.read") ? count("training_enrollments", "status in ('ASSIGNED','IN_PROGRESS')", companyId) : null,
            allowed(authentication, "hr.offboarding.read") ? count("offboarding_cases", "status not in ('COMPLETED','CANCELLED')", companyId) : null);
    }

    public List<EmployeeView> employees(UUID companyId, Authentication authentication) {
        boolean identityPermission = authentication.getAuthorities().stream()
            .anyMatch(authority -> authority.getAuthority().equals("hr.employee.read.basic"));
        return jdbc.sql("""
            select e.id,e.employee_code,e.full_name,e.work_email,e.branch_id,b.name branch_name,
                   e.department_id,d.name department_name,e.position_id,p.title position_title,
                   e.job_title,e.manager_employee_id,e.join_date,e.employment_status
            from employees e left join company_branches b on b.id=e.branch_id
              left join departments d on d.id=e.department_id
              left join job_positions p on p.id=e.position_id
            where e.company_id=:companyId and (
              :identityPermission=true or exists (
                select 1 from company_membership_scopes s
                where s.company_id=e.company_id and s.keycloak_subject=:subject and (
                  (s.scope_type='COMPANY' and s.scope_id=e.company_id)
                  or (s.scope_type='SELF' and s.scope_id=e.id)
                  or (s.scope_type='TEAM' and (s.scope_id=e.id or s.scope_id=e.manager_employee_id))
                  or (s.scope_type='DEPARTMENT' and s.scope_id=e.department_id)
                  or (s.scope_type='BRANCH' and (s.scope_id=e.branch_id or s.scope_id=d.branch_id))
                  or (s.scope_type='PROJECT' and exists (
                    select 1 from pm_project_members pm where pm.company_id=e.company_id
                      and pm.employee_id=e.id and pm.project_id=s.scope_id))
                )
              )
            ) order by e.employee_code
            """).param("companyId", companyId.toString()).param("identityPermission", identityPermission)
            .param("subject", authentication.getName()).query(this::employeeView).list();
    }

    public EmployeeView ownEmployee(UUID companyId, String keycloakSubject) {
        return jdbc.sql("""
            select e.id,e.employee_code,e.full_name,e.work_email,e.branch_id,b.name branch_name,
                   e.department_id,d.name department_name,e.position_id,p.title position_title,
                   e.job_title,e.manager_employee_id,e.join_date,e.employment_status
            from employees e left join company_branches b on b.id=e.branch_id
              left join departments d on d.id=e.department_id
              left join job_positions p on p.id=e.position_id
            where e.company_id=:companyId and e.keycloak_subject=:subject
            """).param("companyId", companyId.toString()).param("subject", keycloakSubject)
            .query(this::employeeView).optional().orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Employee identity is not linked"));
    }

    @Transactional
    public EmployeeView createEmployee(UUID companyId, EmployeeCreateRequest request, String actorSubject) {
        ensureEmployeeOrganization(companyId, request.branchId(), request.departmentId(), request.positionId());
        if (request.managerEmployeeId() != null) {
            ensureCompanyResource("employees", companyId, request.managerEmployeeId(), "Manager");
        }
        UUID id = UUID.randomUUID();
        jdbc.sql("""
            insert into employees(id,company_id,employee_code,keycloak_subject,full_name,work_email,branch_id,
              department_id,position_id,job_title,manager_employee_id,join_date,employment_status)
            values(:id,:companyId,:code,:subject,:name,:email,:branchId,:departmentId,:positionId,
              :title,:managerId,:joinDate,'ACTIVE')
            """).param("id", id.toString()).param("companyId", companyId.toString())
            .param("code", request.employeeCode()).param("subject", blankToNull(request.keycloakSubject()))
            .param("name", request.fullName()).param("email", blankToNull(request.workEmail()))
            .param("branchId", uuid(request.branchId())).param("departmentId", uuid(request.departmentId()))
            .param("positionId", uuid(request.positionId())).param("title", request.jobTitle())
            .param("managerId", uuid(request.managerEmployeeId())).param("joinDate", request.joinDate()).update();
        if (request.privateProfile() != null) savePrivate(companyId, id, request.privateProfile());
        audit.write(companyId, actorSubject, "HR_EMPLOYEE_CREATED", "EMPLOYEE", id.toString(), "{\"source\":\"HR_API\"}");
        return employee(companyId, id);
    }

    @Transactional
    public EmployeeView updateEmployee(UUID companyId, UUID employeeId, EmployeeUpdateRequest request, String actor) {
        ensureCompanyResource("employees", companyId, employeeId, "Employee");
        ensureEmployeeOrganization(companyId, request.branchId(), request.departmentId(), request.positionId());
        jdbc.sql("""
            update employees set full_name=coalesce(:name,full_name),work_email=coalesce(:email,work_email),
              branch_id=coalesce(:branchId,branch_id),department_id=coalesce(:departmentId,department_id),
              position_id=coalesce(:positionId,position_id),job_title=coalesce(:title,job_title),
              employment_status=coalesce(:status,employment_status)
            where id=:id and company_id=:companyId
            """).param("name", blankToNull(request.fullName())).param("email", blankToNull(request.workEmail()))
            .param("branchId", uuid(request.branchId())).param("departmentId", uuid(request.departmentId()))
            .param("positionId", uuid(request.positionId())).param("title", blankToNull(request.jobTitle()))
            .param("status", blankToNull(request.employmentStatus()))
            .param("id", employeeId.toString()).param("companyId", companyId.toString()).update();
        if (request.managerEmployeeId() != null) ensureCompanyResource("employees", companyId, request.managerEmployeeId(), "Manager");
        if (employeeId.equals(request.managerEmployeeId())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Employee cannot manage themselves");
        }
        if (request.managerEmployeeId() != null) {
            jdbc.sql("update employees set manager_employee_id=:managerId where id=:id and company_id=:companyId")
                .param("managerId", request.managerEmployeeId().toString()).param("id", employeeId.toString())
                .param("companyId", companyId.toString()).update();
        }
        audit.write(companyId, actor, "HR_EMPLOYEE_UPDATED", "EMPLOYEE", employeeId.toString(), "{}");
        return employee(companyId, employeeId);
    }

    @Transactional
    public PrivateProfileView updatePrivateProfile(UUID companyId, UUID employeeId, PrivateProfileUpdateRequest request, String actor) {
        ensureCompanyResource("employees", companyId, employeeId, "Employee");
        jdbc.sql("""
            insert into employee_private_profiles(employee_id,company_id,date_of_birth,identity_type,identity_number_ciphertext,
              personal_email_ciphertext,personal_phone_ciphertext,address_ciphertext,emergency_name_ciphertext,emergency_phone_ciphertext)
            values(:employeeId,:companyId,:dob,:identityType,:identityNumber,:personalEmail,:personalPhone,:address,:emergencyName,:emergencyPhone)
            on conflict (employee_id) do update set date_of_birth=coalesce(:dob2,employee_private_profiles.date_of_birth),identity_type=coalesce(:identityType2,employee_private_profiles.identity_type),
              identity_number_ciphertext=coalesce(:identityNumber2,employee_private_profiles.identity_number_ciphertext),
              personal_email_ciphertext=coalesce(:personalEmail2,employee_private_profiles.personal_email_ciphertext),
              personal_phone_ciphertext=coalesce(:personalPhone2,employee_private_profiles.personal_phone_ciphertext),
              address_ciphertext=coalesce(:address2,employee_private_profiles.address_ciphertext),
              emergency_name_ciphertext=coalesce(:emergencyName2,employee_private_profiles.emergency_name_ciphertext),
              emergency_phone_ciphertext=coalesce(:emergencyPhone2,employee_private_profiles.emergency_phone_ciphertext)
            """)
            .param("employeeId", employeeId.toString()).param("companyId", companyId.toString())
            .param("dob", request.dateOfBirth()).param("identityType", blankToNull(request.identityType()))
            .param("identityNumber", cipher.encrypt(request.identityNumber())).param("personalEmail", cipher.encrypt(request.personalEmail()))
            .param("personalPhone", cipher.encrypt(request.personalPhone())).param("address", cipher.encrypt(request.address()))
            .param("emergencyName", cipher.encrypt(request.emergencyName())).param("emergencyPhone", cipher.encrypt(request.emergencyPhone()))
            .param("dob2", request.dateOfBirth()).param("identityType2", blankToNull(request.identityType()))
            .param("identityNumber2", cipher.encrypt(request.identityNumber())).param("personalEmail2", cipher.encrypt(request.personalEmail()))
            .param("personalPhone2", cipher.encrypt(request.personalPhone())).param("address2", cipher.encrypt(request.address()))
            .param("emergencyName2", cipher.encrypt(request.emergencyName())).param("emergencyPhone2", cipher.encrypt(request.emergencyPhone()))
            .update();
        audit.write(companyId, actor, "HR_EMPLOYEE_PRIVATE_PROFILE_UPDATED", "EMPLOYEE", employeeId.toString(), "{}");
        return privateProfile(companyId, employeeId);
    }

    public PrivateProfileView privateProfile(UUID companyId, UUID employeeId) {
        return jdbc.sql("""
            select p.date_of_birth,p.identity_type,p.identity_number_ciphertext,p.personal_email_ciphertext,
              p.personal_phone_ciphertext,p.address_ciphertext,p.emergency_name_ciphertext,p.emergency_phone_ciphertext
            from employee_private_profiles p where p.company_id=:companyId and p.employee_id=:employeeId
            """).param("companyId", companyId.toString()).param("employeeId", employeeId.toString())
            .query((rs, row) -> new PrivateProfileView(rs.getObject("date_of_birth", LocalDate.class), rs.getString("identity_type"),
                cipher.decrypt(rs.getString("identity_number_ciphertext")), cipher.decrypt(rs.getString("personal_email_ciphertext")),
                cipher.decrypt(rs.getString("personal_phone_ciphertext")), cipher.decrypt(rs.getString("address_ciphertext")),
                cipher.decrypt(rs.getString("emergency_name_ciphertext")), cipher.decrypt(rs.getString("emergency_phone_ciphertext"))))
            .optional().orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Private employee profile not found"));
    }

    @Transactional
    public CreatedResource createPosition(UUID companyId, RecruitmentPositionRequest request, String actor) {
        if (request.openings() < 1) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Openings must be positive");
        UUID id = UUID.randomUUID();
        jdbc.sql("""
            insert into recruitment_positions(id,company_id,department_id,code,title,openings,status,
              hiring_manager_employee_id,description,opened_on,closes_on)
            values(:id,:companyId,:departmentId,:code,:title,:openings,'OPEN',:managerId,:description,current_date,:closesOn)
            """).param("id", id.toString()).param("companyId", companyId.toString())
            .param("departmentId", uuid(request.departmentId())).param("code", request.code().trim())
            .param("title", request.title().trim()).param("openings", request.openings())
            .param("managerId", uuid(request.hiringManagerEmployeeId())).param("description", blankToNull(request.description()))
            .param("closesOn", request.closesOn()).update();
        audit.write(companyId, actor, "HR_RECRUITMENT_POSITION_CREATED", "RECRUITMENT_POSITION", id.toString(), "{}");
        return new CreatedResource(id, "OPEN");
    }

    @Transactional
    public CreatedResource createCandidate(UUID companyId, CandidateRequest request, String actor) {
        ensureCompanyResource("recruitment_positions", companyId, request.positionId(), "Recruitment position");
        UUID id = UUID.randomUUID();
        jdbc.sql("""
            insert into recruitment_candidates(id,company_id,position_id,full_name,email_ciphertext,phone_ciphertext,
              cv_object_key,source,stage,status)
            values(:id,:companyId,:positionId,:fullName,:email,:phone,:cvObjectKey,:source,'APPLIED','ACTIVE')
            """).param("id", id.toString()).param("companyId", companyId.toString())
            .param("positionId", request.positionId().toString()).param("fullName", request.fullName().trim())
            .param("email", cipher.encrypt(request.email())).param("phone", cipher.encrypt(request.phone()))
            .param("cvObjectKey", blankToNull(request.cvObjectKey())).param("source", blankToNull(request.source())).update();
        audit.write(companyId, actor, "HR_CANDIDATE_CREATED", "RECRUITMENT_CANDIDATE", id.toString(), "{}");
        return new CreatedResource(id, "APPLIED");
    }

    @Transactional
    public CreatedResource scheduleInterview(UUID companyId, InterviewRequest request, String actor) {
        ensureCompanyResource("recruitment_candidates", companyId, request.candidateId(), "Candidate");
        if (request.durationMinutes() < 15 || request.durationMinutes() > 480) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Interview duration must be between 15 and 480 minutes");
        }
        UUID id = UUID.randomUUID();
        jdbc.sql("""
            insert into recruitment_interviews(id,company_id,candidate_id,round_name,scheduled_at,duration_minutes,
              location_or_url,interviewer_subjects,status)
            values(:id,:companyId,:candidateId,:roundName,:scheduledAt,:duration,:location,'[]','SCHEDULED')
            """).param("id", id.toString()).param("companyId", companyId.toString())
            .param("candidateId", request.candidateId().toString()).param("roundName", request.roundName().trim())
            .param("scheduledAt", Timestamp.from(request.scheduledAt().toInstant())).param("duration", request.durationMinutes())
            .param("location", blankToNull(request.locationOrUrl())).update();
        audit.write(companyId, actor, "HR_INTERVIEW_SCHEDULED", "RECRUITMENT_INTERVIEW", id.toString(), "{}");
        return new CreatedResource(id, "SCHEDULED");
    }

    @Transactional
    public CreatedResource createOnboarding(UUID companyId, OnboardingRequest request, String actor) {
        ensureCompanyResource("employees", companyId, request.employeeId(), "Employee");
        if (request.mentorEmployeeId() != null) ensureCompanyResource("employees", companyId, request.mentorEmployeeId(), "Mentor");
        UUID id = UUID.randomUUID();
        jdbc.sql("""
            insert into onboarding_cases(id,company_id,employee_id,start_date,mentor_employee_id,status,progress_percent)
            values(:id,:companyId,:employeeId,:startDate,:mentorId,'PLANNED',0)
            """).param("id", id.toString()).param("companyId", companyId.toString())
            .param("employeeId", request.employeeId().toString()).param("startDate", request.startDate())
            .param("mentorId", uuid(request.mentorEmployeeId())).update();
        createChecklist(companyId, id, "onboarding_tasks", "onboarding_case_id",
            List.of(new ChecklistItem("ACCOUNT", "Cấp tài khoản làm việc"),
                    new ChecklistItem("EQUIPMENT", "Bàn giao thiết bị"),
                    new ChecklistItem("TRAINING", "Đào tạo hội nhập"),
                    new ChecklistItem("MENTOR", "Phân công người hướng dẫn")), request.startDate());
        audit.write(companyId, actor, "HR_ONBOARDING_CREATED", "ONBOARDING_CASE", id.toString(), "{}");
        return new CreatedResource(id, "PLANNED");
    }

    @Transactional
    public CreatedResource checkIn(UUID companyId, String subject) {
        UUID employeeId = ownEmployeeId(companyId, subject);
        boolean exists = jdbc.sql("select count(*) from attendance_entries where company_id=:companyId and employee_id=:employeeId and work_date=current_date")
            .param("companyId", companyId.toString()).param("employeeId", employeeId.toString()).query(Long.class).single() > 0;
        if (exists) throw new ResponseStatusException(HttpStatus.CONFLICT, "Employee has already checked in today");
        UUID id = UUID.randomUUID();
        jdbc.sql("""
            insert into attendance_entries(id,company_id,employee_id,work_date,check_in,source,status)
            values(:id,:companyId,:employeeId,current_date,current_timestamp,'WEB','RECORDED')
            """).param("id", id.toString()).param("companyId", companyId.toString())
            .param("employeeId", employeeId.toString()).update();
        audit.write(companyId, subject, "HR_ATTENDANCE_CHECK_IN", "ATTENDANCE", id.toString(), "{}");
        return new CreatedResource(id, "RECORDED");
    }

    @Transactional
    public CreatedResource checkOut(UUID companyId, String subject) {
        UUID employeeId = ownEmployeeId(companyId, subject);
        UUID id = jdbc.sql("select id from attendance_entries where company_id=:companyId and employee_id=:employeeId and work_date=current_date and check_out is null")
            .param("companyId", companyId.toString()).param("employeeId", employeeId.toString()).query(String.class)
            .optional().map(UUID::fromString).orElseThrow(() -> new ResponseStatusException(HttpStatus.CONFLICT, "No open attendance entry for today"));
        jdbc.sql("""
            update attendance_entries set check_out=current_timestamp,
              worked_minutes=greatest(floor(extract(epoch from (current_timestamp-check_in))/60),0)::int
            where id=:id and company_id=:companyId
            """).param("id", id.toString()).param("companyId", companyId.toString()).update();
        audit.write(companyId, subject, "HR_ATTENDANCE_CHECK_OUT", "ATTENDANCE", id.toString(), "{}");
        return new CreatedResource(id, "RECORDED");
    }

    @Transactional
    public CreatedResource requestAttendanceAdjustment(UUID companyId, AttendanceAdjustmentRequest request, String subject) {
        UUID employeeId = ownEmployeeId(companyId, subject);
        boolean ownsEntry = jdbc.sql("select count(*) from attendance_entries where id=:id and company_id=:companyId and employee_id=:employeeId")
            .param("id", request.attendanceEntryId().toString()).param("companyId", companyId.toString())
            .param("employeeId", employeeId.toString()).query(Long.class).single() > 0;
        if (!ownsEntry) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Attendance entry not found");
        UUID id = UUID.randomUUID();
        jdbc.sql("""
            insert into attendance_adjustment_requests(id,company_id,attendance_entry_id,requested_by_subject,
              proposed_check_in,proposed_check_out,reason,status)
            values(:id,:companyId,:entryId,:subject,:checkIn,:checkOut,:reason,'PENDING')
            """).param("id", id.toString()).param("companyId", companyId.toString())
            .param("entryId", request.attendanceEntryId().toString()).param("subject", subject)
            .param("checkIn", timestamp(request.proposedCheckIn())).param("checkOut", timestamp(request.proposedCheckOut()))
            .param("reason", request.reason().trim()).update();
        audit.write(companyId, subject, "HR_ATTENDANCE_ADJUSTMENT_REQUESTED", "ATTENDANCE_ADJUSTMENT", id.toString(), "{}");
        return new CreatedResource(id, "PENDING");
    }

    @Transactional
    public CreatedResource reviewAttendanceAdjustment(UUID companyId, UUID requestId, ReviewRequest request, String actor) {
        Adjustment adjustment = jdbc.sql("""
            select attendance_entry_id,proposed_check_in,proposed_check_out from attendance_adjustment_requests
            where id=:id and company_id=:companyId and status='PENDING'
            """).param("id", requestId.toString()).param("companyId", companyId.toString())
            .query((rs, row) -> new Adjustment(UUID.fromString(rs.getString(1)), rs.getTimestamp(2), rs.getTimestamp(3)))
            .optional().orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Pending adjustment request not found"));
        jdbc.sql("""
            update attendance_adjustment_requests set status=:status,reviewed_by_subject=:actor,
              reviewed_at=current_timestamp,review_note=:note where id=:id and company_id=:companyId
            """).param("status", request.decision()).param("actor", actor).param("note", blankToNull(request.note()))
            .param("id", requestId.toString()).param("companyId", companyId.toString()).update();
        if ("APPROVED".equals(request.decision())) {
            jdbc.sql("""
                update attendance_entries set check_in=coalesce(:checkIn,check_in),check_out=coalesce(:checkOut,check_out),
                  worked_minutes=case when coalesce(:checkIn,check_in) is not null and coalesce(:checkOut,check_out) is not null
                    then greatest(floor(extract(epoch from (coalesce(:checkOut,check_out)-coalesce(:checkIn,check_in)))/60),0)::int else worked_minutes end,
                  status='ADJUSTED' where id=:entryId and company_id=:companyId
                """).param("checkIn", adjustment.checkIn()).param("checkOut", adjustment.checkOut())
                .param("entryId", adjustment.entryId().toString()).param("companyId", companyId.toString()).update();
        }
        audit.write(companyId, actor, "HR_ATTENDANCE_ADJUSTMENT_" + request.decision(), "ATTENDANCE_ADJUSTMENT", requestId.toString(), "{}");
        return new CreatedResource(requestId, request.decision());
    }

    public List<Map<String, Object>> recruitmentCandidates(UUID companyId) {
        return rows("""
            select c.id,c.position_id,p.title position_title,c.full_name,c.stage,c.status,c.cv_object_key,c.source,c.applied_at
            from recruitment_candidates c join recruitment_positions p on p.id=c.position_id
            where c.company_id=:companyId order by c.applied_at desc limit 500
            """, companyId);
    }
    public List<Map<String, Object>> recruitmentInterviews(UUID companyId) {
        return rows("""
            select i.id,i.candidate_id,c.full_name candidate_name,p.title position_title,i.round_name,i.scheduled_at,
              i.duration_minutes,i.location_or_url,i.status,i.score,i.recommendation
            from recruitment_interviews i join recruitment_candidates c on c.id=i.candidate_id
            join recruitment_positions p on p.id=c.position_id where i.company_id=:companyId order by i.scheduled_at desc limit 500
            """, companyId);
    }
    public List<Map<String, Object>> recruitmentOffers(UUID companyId) {
        return rows("""
            select o.id,o.candidate_id,c.full_name candidate_name,o.status,o.proposed_title,o.proposed_start_date,o.sent_at,o.responded_at
            from recruitment_offers o join recruitment_candidates c on c.id=o.candidate_id
            where o.company_id=:companyId order by o.sent_at desc, o.id desc limit 500
            """, companyId);
    }
    public List<Map<String, Object>> onboardingChecklist(UUID companyId, UUID caseId) {
        ensureCompanyResource("onboarding_cases", companyId, caseId, "Onboarding case");
        return namedJdbc.queryForList("""
            select id,category,title,assignee_subject,due_on,status,completed_at
            from onboarding_tasks where company_id=:companyId and onboarding_case_id=:caseId order by category
            """, Map.of("companyId", companyId.toString(), "caseId", caseId.toString()));
    }
    public List<Map<String, Object>> offboardingChecklist(UUID companyId, UUID caseId) {
        ensureCompanyResource("offboarding_cases", companyId, caseId, "Offboarding case");
        return namedJdbc.queryForList("""
            select id,category,title,assignee_subject,asset_reference,account_reference,due_on,status,completed_at
            from offboarding_tasks where company_id=:companyId and offboarding_case_id=:caseId order by category
            """, Map.of("companyId", companyId.toString(), "caseId", caseId.toString()));
    }
    public List<Map<String, Object>> trainingCourses(UUID companyId) {
        return rows("select id,code,title,delivery_type,passing_score,active from training_courses where company_id=:companyId and active=true order by created_at desc limit 200", companyId);
    }
    public List<Map<String, Object>> performanceCycles(UUID companyId) {
        return rows("select id,name,cycle_type,starts_on,ends_on,status from performance_cycles where company_id=:companyId order by starts_on desc limit 200", companyId);
    }

    public List<Map<String, Object>> recruitment(UUID companyId) {
        return rows("select id,code,title,openings,status,opened_on,closes_on from recruitment_positions where company_id=:companyId order by created_at desc", companyId);
    }
    public List<Map<String, Object>> onboarding(UUID companyId) {
        return rows("select o.id,o.employee_id,e.employee_code,e.full_name,o.start_date,o.status,o.progress_percent from onboarding_cases o join employees e on e.id=o.employee_id where o.company_id=:companyId order by o.start_date desc", companyId);
    }
    public List<Map<String, Object>> attendance(UUID companyId) {
        return rows("select a.id,a.employee_id,e.employee_code,e.full_name,a.work_date,a.check_in,a.check_out,a.worked_minutes,a.late_minutes,a.overtime_minutes,a.status from attendance_entries a join employees e on e.id=a.employee_id where a.company_id=:companyId order by a.work_date desc limit 500", companyId);
    }
    public List<Map<String, Object>> leaveRequests(UUID companyId) {
        return rows("select r.id,r.employee_id,e.employee_code,e.full_name,t.name leave_type,r.starts_on,r.ends_on,r.requested_days,r.status from leave_requests r join employees e on e.id=r.employee_id join leave_types t on t.id=r.leave_type_id where r.company_id=:companyId order by r.created_at desc", companyId);
    }
    public List<Map<String, Object>> leaveTypes(UUID companyId) {
        return rows("select id,code,name,paid,annual_allowance,requires_document from leave_types where company_id=:companyId and active=true order by name", companyId);
    }
    public List<Map<String, Object>> ownLeaveBalances(UUID companyId, String subject) {
        UUID employeeId = ownEmployeeId(companyId, subject);
        return namedJdbc.queryForList("""
            select b.id,t.code,t.name,b.balance_year,b.entitled_days,b.used_days,b.pending_days,
              b.entitled_days-b.used_days-b.pending_days remaining_days
            from leave_balances b join leave_types t on t.id=b.leave_type_id
            where b.company_id=:companyId and b.employee_id=:employeeId order by b.balance_year desc,t.name
            """, Map.of("companyId", companyId.toString(), "employeeId", employeeId.toString()));
    }
    public List<Map<String, Object>> payrollRuns(UUID companyId) {
        return rows("select id,period_start,period_end,status,currency,approved_at,paid_at from payroll_runs where company_id=:companyId order by period_start desc", companyId);
    }
    public List<Map<String, Object>> performance(UUID companyId) {
        return rows("select r.id,r.cycle_id,r.employee_id,e.employee_code,e.full_name,r.review_type,r.status,r.score,r.submitted_at from performance_reviews r join employees e on e.id=r.employee_id where r.company_id=:companyId order by r.submitted_at desc", companyId);
    }
    public List<Map<String, Object>> training(UUID companyId) {
        return rows("select n.id,n.employee_id,e.employee_code,e.full_name,c.title course_title,n.status,n.progress_percent,n.score,n.completed_at from training_enrollments n join employees e on e.id=n.employee_id join training_courses c on c.id=n.course_id where n.company_id=:companyId order by n.assigned_at desc", companyId);
    }
    public List<Map<String, Object>> offboarding(UUID companyId) {
        return rows("select o.id,o.employee_id,e.employee_code,e.full_name,o.resignation_submitted_on,o.last_working_day,o.reason_category,o.status from offboarding_cases o join employees e on e.id=o.employee_id where o.company_id=:companyId order by o.last_working_day desc", companyId);
    }

    @Transactional
    public CreatedResource requestLeave(UUID companyId, LeaveRequest request, String subject) {
        if (request.endsOn().isBefore(request.startsOn())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Leave end date must be on or after start date");
        }
        UUID employeeId = ownEmployeeId(companyId, subject);
        ensureCompanyResource("leave_types", companyId, request.leaveTypeId(), "Leave type");
        UUID id = UUID.randomUUID();
        jdbc.sql("""
            insert into leave_requests(id,company_id,employee_id,leave_type_id,starts_on,ends_on,requested_days,
              reason,document_object_key,status)
            values(:id,:companyId,:employeeId,:typeId,:startsOn,:endsOn,:days,:reason,:documentKey,'PENDING')
            """).param("id", id.toString()).param("companyId", companyId.toString())
            .param("employeeId", employeeId.toString()).param("typeId", request.leaveTypeId().toString())
            .param("startsOn", request.startsOn()).param("endsOn", request.endsOn()).param("days", request.requestedDays())
            .param("reason", blankToNull(request.reason())).param("documentKey", blankToNull(request.documentObjectKey())).update();
        jdbc.sql("""
            update leave_balances set pending_days=pending_days+:days
            where company_id=:companyId and employee_id=:employeeId and leave_type_id=:typeId and balance_year=:year
            """).param("days", request.requestedDays()).param("companyId", companyId.toString())
            .param("employeeId", employeeId.toString()).param("typeId", request.leaveTypeId().toString())
            .param("year", request.startsOn().getYear()).update();
        audit.write(companyId, subject, "HR_LEAVE_REQUESTED", "LEAVE_REQUEST", id.toString(), "{}");
        return new CreatedResource(id, "PENDING");
    }

    @Transactional
    public CreatedResource reviewLeave(UUID companyId, UUID requestId, ReviewRequest request, String actor) {
        LeaveReview leave = jdbc.sql("""
            select employee_id,leave_type_id,extract(year from starts_on)::int,requested_days from leave_requests
            where id=:id and company_id=:companyId and status='PENDING'
            """).param("id", requestId.toString()).param("companyId", companyId.toString())
            .query((rs, row) -> new LeaveReview(UUID.fromString(rs.getString(1)), UUID.fromString(rs.getString(2)),
                rs.getInt(3), rs.getBigDecimal(4))).optional()
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Pending leave request not found"));
        if ("APPROVED".equals(request.decision())) {
            var balance = jdbc.sql("""
                select entitled_days-used_days-pending_days from leave_balances
                where company_id=:companyId and employee_id=:employeeId and leave_type_id=:typeId and balance_year=:year
                """).param("companyId", companyId.toString()).param("employeeId", leave.employeeId().toString())
                .param("typeId", leave.leaveTypeId().toString()).param("year", leave.year())
                .query(java.math.BigDecimal.class).optional();
            if (balance.isPresent() && balance.get().add(leave.days()).compareTo(leave.days()) < 0) {
                throw new ResponseStatusException(HttpStatus.CONFLICT, "Insufficient leave balance");
            }
        }
        jdbc.sql("""
            update leave_requests set status=:status,approver_subject=:actor,reviewed_at=current_timestamp,review_note=:note
            where id=:id and company_id=:companyId and status='PENDING'
            """).param("status", request.decision()).param("actor", actor).param("note", blankToNull(request.note()))
            .param("id", requestId.toString()).param("companyId", companyId.toString()).update();
        jdbc.sql("""
            update leave_balances set pending_days=greatest(pending_days-:days,0),
              used_days=used_days + case when :approved=true then :days else 0 end
            where company_id=:companyId and employee_id=:employeeId and leave_type_id=:typeId and balance_year=:year
            """).param("days", leave.days()).param("approved", "APPROVED".equals(request.decision()))
            .param("companyId", companyId.toString()).param("employeeId", leave.employeeId().toString())
            .param("typeId", leave.leaveTypeId().toString()).param("year", leave.year()).update();
        audit.write(companyId, actor, "HR_LEAVE_" + request.decision(), "LEAVE_REQUEST", requestId.toString(), "{}");
        return new CreatedResource(requestId, request.decision());
    }

    @Transactional
    public CreatedResource createSalaryStructure(UUID companyId, SalaryStructureRequest request, String actor) {
        if (request.effectiveTo() != null && request.effectiveTo().isBefore(request.effectiveFrom())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Salary effective end date is invalid");
        }
        ensureCompanyResource("employees", companyId, request.employeeId(), "Employee");
        UUID id = UUID.randomUUID();
        jdbc.sql("""
            insert into salary_structures(id,company_id,employee_id,effective_from,effective_to,currency,
              base_salary_ciphertext,allowances_ciphertext,deductions_ciphertext,created_by_subject)
            values(:id,:companyId,:employeeId,:effectiveFrom,:effectiveTo,:currency,:baseSalary,:allowances,:deductions,:actor)
            """).param("id", id.toString()).param("companyId", companyId.toString())
            .param("employeeId", request.employeeId().toString()).param("effectiveFrom", request.effectiveFrom())
            .param("effectiveTo", request.effectiveTo()).param("currency", request.currency())
            .param("baseSalary", cipher.encrypt(request.baseSalary().toPlainString()))
            .param("allowances", cipher.encrypt(decimal(request.allowances())))
            .param("deductions", cipher.encrypt(decimal(request.deductions()))).param("actor", actor).update();
        audit.write(companyId, actor, "HR_SALARY_STRUCTURE_CREATED", "SALARY_STRUCTURE", id.toString(), "{}");
        return new CreatedResource(id, "ACTIVE");
    }

    @Transactional
    public CreatedResource createPayrollRun(UUID companyId, PayrollRunRequest request, String actor) {
        if (request.periodEnd().isBefore(request.periodStart())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Payroll period is invalid");
        }
        UUID id = UUID.randomUUID();
        jdbc.sql("""
            insert into payroll_runs(id,company_id,period_start,period_end,status,currency)
            values(:id,:companyId,:periodStart,:periodEnd,'DRAFT',:currency)
            """).param("id", id.toString()).param("companyId", companyId.toString())
            .param("periodStart", request.periodStart()).param("periodEnd", request.periodEnd())
            .param("currency", request.currency()).update();
        audit.write(companyId, actor, "HR_PAYROLL_RUN_CREATED", "PAYROLL_RUN", id.toString(), "{}");
        return new CreatedResource(id, "DRAFT");
    }

    @Transactional
    public CreatedResource createPerformanceCycle(UUID companyId, PerformanceCycleRequest request, String actor) {
        if (request.endsOn().isBefore(request.startsOn())) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Performance cycle is invalid");
        UUID id = UUID.randomUUID();
        jdbc.sql("""
            insert into performance_cycles(id,company_id,name,cycle_type,starts_on,ends_on,status)
            values(:id,:companyId,:name,:cycleType,:startsOn,:endsOn,'DRAFT')
            """).param("id", id.toString()).param("companyId", companyId.toString())
            .param("name", request.name().trim()).param("cycleType", request.cycleType().trim())
            .param("startsOn", request.startsOn()).param("endsOn", request.endsOn()).update();
        audit.write(companyId, actor, "HR_PERFORMANCE_CYCLE_CREATED", "PERFORMANCE_CYCLE", id.toString(), "{}");
        return new CreatedResource(id, "DRAFT");
    }

    @Transactional
    public CreatedResource createObjective(UUID companyId, ObjectiveRequest request, String actor) {
        ensureCompanyResource("performance_cycles", companyId, request.cycleId(), "Performance cycle");
        ensureCompanyResource("employees", companyId, request.employeeId(), "Employee");
        UUID id = UUID.randomUUID();
        jdbc.sql("""
            insert into employee_objectives(id,company_id,cycle_id,employee_id,objective_type,title,description,
              target_value,current_value,weight,status)
            values(:id,:companyId,:cycleId,:employeeId,:type,:title,:description,:target,0,:weight,'ACTIVE')
            """).param("id", id.toString()).param("companyId", companyId.toString())
            .param("cycleId", request.cycleId().toString()).param("employeeId", request.employeeId().toString())
            .param("type", request.objectiveType()).param("title", request.title().trim())
            .param("description", blankToNull(request.description())).param("target", request.targetValue())
            .param("weight", request.weight()).update();
        audit.write(companyId, actor, "HR_OBJECTIVE_CREATED", "EMPLOYEE_OBJECTIVE", id.toString(), "{}");
        return new CreatedResource(id, "ACTIVE");
    }

    @Transactional
    public CreatedResource createCourse(UUID companyId, TrainingCourseRequest request, String actor) {
        UUID id = UUID.randomUUID();
        jdbc.sql("""
            insert into training_courses(id,company_id,code,title,description,delivery_type,passing_score,active)
            values(:id,:companyId,:code,:title,:description,:deliveryType,:passingScore,true)
            """).param("id", id.toString()).param("companyId", companyId.toString())
            .param("code", request.code().trim()).param("title", request.title().trim())
            .param("description", blankToNull(request.description())).param("deliveryType", request.deliveryType().trim())
            .param("passingScore", request.passingScore()).update();
        audit.write(companyId, actor, "HR_TRAINING_COURSE_CREATED", "TRAINING_COURSE", id.toString(), "{}");
        return new CreatedResource(id, "ACTIVE");
    }

    @Transactional
    public CreatedResource enrollTraining(UUID companyId, TrainingEnrollmentRequest request, String actor) {
        ensureCompanyResource("training_courses", companyId, request.courseId(), "Training course");
        ensureCompanyResource("employees", companyId, request.employeeId(), "Employee");
        UUID id = UUID.randomUUID();
        jdbc.sql("""
            insert into training_enrollments(id,company_id,course_id,employee_id,status,progress_percent)
            values(:id,:companyId,:courseId,:employeeId,'ASSIGNED',0)
            """).param("id", id.toString()).param("companyId", companyId.toString())
            .param("courseId", request.courseId().toString()).param("employeeId", request.employeeId().toString()).update();
        audit.write(companyId, actor, "HR_TRAINING_ASSIGNED", "TRAINING_ENROLLMENT", id.toString(), "{}");
        return new CreatedResource(id, "ASSIGNED");
    }

    @Transactional
    public CreatedResource createOffboarding(UUID companyId, OffboardingRequest request, String actor) {
        ensureCompanyResource("employees", companyId, request.employeeId(), "Employee");
        UUID id = UUID.randomUUID();
        jdbc.sql("""
            insert into offboarding_cases(id,company_id,employee_id,resignation_submitted_on,last_working_day,
              reason_category,reason_ciphertext,status,archive_after)
            values(:id,:companyId,:employeeId,:submittedOn,:lastWorkingDay,:category,:reason,'REQUESTED',(:lastWorkingDay + interval '7 years')::date)
            """).param("id", id.toString()).param("companyId", companyId.toString())
            .param("employeeId", request.employeeId().toString()).param("submittedOn", request.resignationSubmittedOn())
            .param("lastWorkingDay", request.lastWorkingDay()).param("category", blankToNull(request.reasonCategory()))
            .param("reason", cipher.encrypt(request.reason())).update();
        createChecklist(companyId, id, "offboarding_tasks", "offboarding_case_id",
            List.of(new ChecklistItem("HANDOVER", "Bàn giao công việc"),
                    new ChecklistItem("ASSET", "Thu hồi tài sản"),
                    new ChecklistItem("ACCOUNT", "Thu hồi tài khoản"),
                    new ChecklistItem("OBLIGATION", "Hoàn tất nghĩa vụ")), request.lastWorkingDay());
        audit.write(companyId, actor, "HR_OFFBOARDING_CREATED", "OFFBOARDING_CASE", id.toString(), "{}");
        return new CreatedResource(id, "REQUESTED");
    }

    public List<PayslipView> payslips(UUID companyId, UUID employeeId) {
        return jdbc.sql("""
            select p.id,p.payroll_run_id,p.employee_id,r.period_start,r.period_end,r.currency,
              p.gross_ciphertext,p.deduction_ciphertext,p.net_ciphertext,p.payslip_object_key,p.published_at
            from payslips p join payroll_runs r on r.id=p.payroll_run_id
            where p.company_id=:companyId and p.employee_id=:employeeId order by r.period_start desc
            """).param("companyId", companyId.toString()).param("employeeId", employeeId.toString())
            .query((rs, row) -> new PayslipView(UUID.fromString(rs.getString("id")), UUID.fromString(rs.getString("payroll_run_id")),
                UUID.fromString(rs.getString("employee_id")), rs.getObject("period_start", LocalDate.class),
                rs.getObject("period_end", LocalDate.class), rs.getString("currency"), cipher.decrypt(rs.getString("gross_ciphertext")),
                cipher.decrypt(rs.getString("deduction_ciphertext")), cipher.decrypt(rs.getString("net_ciphertext")),
                rs.getString("payslip_object_key"), instant(rs.getTimestamp("published_at")))).list();
    }


    // === Recruitment: candidate stage, interview evaluation, offers ===

    @Transactional
    public CreatedResource moveCandidate(UUID companyId, CandidateStageRequest request, String actor) {
        ensureCompanyResource("recruitment_candidates", companyId, request.candidateId(), "Candidate");
        jdbc.sql("""
            update recruitment_candidates set stage=:stage
              where id=:id and company_id=:companyId and status='ACTIVE'
            """).param("stage", request.stage()).param("id", request.candidateId().toString())
            .param("companyId", companyId.toString()).update();
        audit.write(companyId, actor, "HR_CANDIDATE_STAGE_CHANGED", "RECRUITMENT_CANDIDATE", request.candidateId().toString(), "{}");
        return new CreatedResource(request.candidateId(), request.stage());
    }

    @Transactional
    public CreatedResource evaluateInterview(UUID companyId, InterviewReviewRequest request, String actor) {
        ensureCompanyResource("recruitment_interviews", companyId, request.interviewId(), "Interview");
        jdbc.sql("""
            update recruitment_interviews set status='COMPLETED', score=:score, recommendation=:recommendation, feedback=:feedback
              where id=:id and company_id=:companyId
            """).param("score", request.score()).param("recommendation", request.recommendation())
            .param("feedback", blankToNull(request.feedback())).param("id", request.interviewId().toString())
            .param("companyId", companyId.toString()).update();
        audit.write(companyId, actor, "HR_INTERVIEW_EVALUATED", "RECRUITMENT_INTERVIEW", request.interviewId().toString(), "{}");
        return new CreatedResource(request.interviewId(), "COMPLETED");
    }

    @Transactional
    public CreatedResource createOffer(UUID companyId, OfferRequest request, String actor) {
        ensureCompanyResource("recruitment_candidates", companyId, request.candidateId(), "Candidate");
        UUID id = UUID.randomUUID();
        jdbc.sql("""
            insert into recruitment_offers(id,company_id,candidate_id,status,proposed_title,proposed_start_date,compensation_ciphertext)
            values(:id,:companyId,:candidateId,'DRAFT',:title,:startDate,:compensation)
            """).param("id", id.toString()).param("companyId", companyId.toString())
            .param("candidateId", request.candidateId().toString()).param("title", request.proposedTitle().trim())
            .param("startDate", request.proposedStartDate())
            .param("compensation", request.compensation() == null ? null : cipher.encrypt(request.compensation().toPlainString()))
            .update();
        jdbc.sql("update recruitment_candidates set stage='OFFER' where id=:id and company_id=:companyId")
            .param("id", request.candidateId().toString()).param("companyId", companyId.toString()).update();
        audit.write(companyId, actor, "HR_OFFER_CREATED", "RECRUITMENT_OFFER", id.toString(), "{}");
        return new CreatedResource(id, "DRAFT");
    }

    @Transactional
    public CreatedResource decideOffer(UUID companyId, OfferDecisionRequest request, String actor) {
        ensureCompanyResource("recruitment_offers", companyId, request.offerId(), "Offer");
        boolean accepted = "ACCEPTED".equals(request.decision());
        jdbc.sql("""
            update recruitment_offers
            set status=:status,
              responded_at=case when :decision='ACCEPTED' then current_timestamp else responded_at end
            where id=:id and company_id=:companyId
            """).param("status", request.decision()).param("decision", request.decision())
            .param("id", request.offerId().toString()).param("companyId", companyId.toString()).update();
        jdbc.sql("""
            update recruitment_candidates set status=case when :accepted=true then 'HIRED' else 'REJECTED' end,
              stage=case when :accepted=true then 'HIRED' else 'REJECTED' end
            where id=(select candidate_id from recruitment_offers where id=:offerId and company_id=:companyId) and company_id=:companyId
            """).param("accepted", accepted).param("companyId", companyId.toString()).param("offerId", request.offerId().toString()).update();
        audit.write(companyId, actor, "HR_OFFER_" + request.decision(), "RECRUITMENT_OFFER", request.offerId().toString(), "{}");
        return new CreatedResource(request.offerId(), request.decision());
    }

    // === Onboarding: checklist tasks ===

    @Transactional
    public CreatedResource addOnboardingTask(UUID companyId, OnboardingTaskRequest request, String actor) {
        ensureCompanyResource("onboarding_cases", companyId, request.onboardingCaseId(), "Onboarding case");
        UUID id = UUID.randomUUID();
        jdbc.sql("""
            insert into onboarding_tasks(id,company_id,onboarding_case_id,category,title,assignee_subject,due_on,status)
            values(:id,:companyId,:caseId,:category,:title,:assignee,:dueOn,'PENDING')
            """).param("id", id.toString()).param("companyId", companyId.toString())
            .param("caseId", request.onboardingCaseId().toString()).param("category", request.category().trim())
            .param("title", request.title().trim()).param("assignee", blankToNull(request.assigneeSubject()))
            .param("dueOn", request.dueOn()).update();
        audit.write(companyId, actor, "HR_ONBOARDING_TASK_ADDED", "ONBOARDING_TASK", id.toString(), "{}");
        return new CreatedResource(id, "PENDING");
    }

    @Transactional
    public CreatedResource completeOnboardingTask(UUID companyId, OnboardingTaskDecisionRequest request, String actor) {
        UUID caseId = jdbc.sql("select onboarding_case_id from onboarding_tasks where id=:id and company_id=:companyId")
            .param("id", request.taskId().toString()).param("companyId", companyId.toString())
            .query(String.class).optional().map(UUID::fromString)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Onboarding task not found"));
        boolean complete = "COMPLETE".equals(request.decision());
        jdbc.sql("""
            update onboarding_tasks set status=:status, completed_at=case when :complete=true then current_timestamp else null end
            where id=:id and company_id=:companyId
            """).param("status", complete ? "COMPLETED" : "PENDING").param("complete", complete)
            .param("id", request.taskId().toString()).param("companyId", companyId.toString()).update();
        updateCaseProgress(companyId, caseId, "onboarding_tasks", "onboarding_case_id");
        audit.write(companyId, actor, complete ? "HR_ONBOARDING_TASK_COMPLETED" : "HR_ONBOARDING_TASK_REOPENED", "ONBOARDING_TASK", request.taskId().toString(), "{}");
        return new CreatedResource(request.taskId(), complete ? "COMPLETED" : "PENDING");
    }

    @Transactional
    public CreatedResource completeOffboardingTask(UUID companyId, OffboardingTaskDecisionRequest request, String actor) {
        UUID caseId = jdbc.sql("select offboarding_case_id from offboarding_tasks where id=:id and company_id=:companyId")
            .param("id", request.taskId().toString()).param("companyId", companyId.toString())
            .query(String.class).optional().map(UUID::fromString)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Offboarding task not found"));
        boolean complete = "COMPLETE".equals(request.decision());
        jdbc.sql("""
            update offboarding_tasks set status=:status, completed_at=case when :complete=true then current_timestamp else null end
            where id=:id and company_id=:companyId
            """).param("status", complete ? "COMPLETED" : "PENDING").param("complete", complete)
            .param("id", request.taskId().toString()).param("companyId", companyId.toString()).update();
        updateCaseProgress(companyId, caseId, "offboarding_tasks", "offboarding_case_id");
        audit.write(companyId, actor, complete ? "HR_OFFBOARDING_TASK_COMPLETED" : "HR_OFFBOARDING_TASK_REOPENED", "OFFBOARDING_TASK", request.taskId().toString(), "{}");
        return new CreatedResource(request.taskId(), complete ? "COMPLETED" : "PENDING");
    }

    // === Attendance: shifts & calendar queries ===

    public List<Map<String, Object>> adjustmentRequests(UUID companyId) {
        return rows("""
            select a.id,a.attendance_entry_id,e.employee_code,e.full_name,a.requested_by_subject,
              a.proposed_check_in,a.proposed_check_out,a.reason,a.status,a.reviewed_at
            from attendance_adjustment_requests a
            join attendance_entries at on at.id=a.attendance_entry_id
            join employees e on e.id=at.employee_id
            where a.company_id=:companyId order by a.created_at desc limit 500
            """, companyId);
    }
    public List<Map<String, Object>> shifts(UUID companyId) {
        return rows("select id,code,name,start_time,end_time,break_minutes,late_grace_minutes,active from work_shifts where company_id=:companyId order by start_time", companyId);
    }

    @Transactional
    public CreatedResource createShift(UUID companyId, WorkShiftRequest request, String actor) {
        if (request.startTime().compareTo(request.endTime()) >= 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Shift start must be before end time");
        }
        UUID id = UUID.randomUUID();
        jdbc.sql("""
            insert into work_shifts(id,company_id,code,name,start_time,end_time,break_minutes,late_grace_minutes,active)
            values(:id,:companyId,:code,:name,:start,:end,:break,:grace,true)
            """).param("id", id.toString()).param("companyId", companyId.toString()).param("code", request.code().trim())
            .param("name", request.name().trim()).param("start", request.startTime())
            .param("end", request.endTime()).param("break", request.breakMinutes() == null ? 0 : request.breakMinutes())
            .param("grace", request.lateGraceMinutes() == null ? 0 : request.lateGraceMinutes()).update();
        audit.write(companyId, actor, "HR_WORK_SHIFT_CREATED", "WORK_SHIFT", id.toString(), "{}");
        return new CreatedResource(id, "ACTIVE");
    }

    public List<Map<String, Object>> attendanceHistory(UUID companyId, String employeeId, LocalDate from, LocalDate to) {
        if (from == null || to == null || to.isBefore(from)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Valid from/to range is required");
        }
        Map<String, Object> params = new java.util.LinkedHashMap<>();
        params.put("companyId", companyId.toString());
        if (employeeId != null) {
            ensureCompanyResource("employees", companyId, UUID.fromString(employeeId), "Employee");
            params.put("employeeId", employeeId);
        }
        String sql = "select a.id,a.employee_id,e.employee_code,e.full_name,a.work_date,a.check_in,a.check_out," +
            "a.worked_minutes,a.late_minutes,a.overtime_minutes,a.status from attendance_entries a " +
            "join employees e on e.id=a.employee_id where a.company_id=:companyId and a.work_date between :fromDate and :toDate";
        if (employeeId != null) sql += " and a.employee_id=:employeeId";
        sql += " order by a.work_date desc limit 2000";
        return namedJdbc.queryForList(sql, params);
    }

    // === Leave: calendar view & balances for a specific employee ===

    public List<Map<String, Object>> leaveCalendar(UUID companyId, LocalDate from, LocalDate to) {
        if (from == null || to == null || to.isBefore(from)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Valid from/to range is required");
        }
        return namedJdbc.queryForList("""
            select r.id,r.employee_id,e.employee_code,e.full_name,t.name leave_type,r.starts_on,r.ends_on,r.status
            from leave_requests r join employees e on e.id=r.employee_id join leave_types t on t.id=r.leave_type_id
            where r.company_id=:companyId and r.status='APPROVED' and r.ends_on>=:fromDate and r.starts_on<=:toDate
            order by r.starts_on
            """, Map.of("companyId", companyId.toString(), "fromDate", from, "toDate", to));
    }

    // === Payroll: approve/reject/paid run + publish payslips from salary structures ===

    @Transactional
    public CreatedResource decidePayrollRun(UUID companyId, PayrollRunDecisionRequest request, String actor) {
        ensureCompanyResource("payroll_runs", companyId, request.runId(), "Payroll run");
        String currentStatus = jdbc.sql("select status from payroll_runs where id=:id and company_id=:companyId")
            .param("id", request.runId().toString()).param("companyId", companyId.toString()).query(String.class).single();
        if ("APPROVED".equals(request.decision()) && !"DRAFT".equals(currentStatus)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Only draft payroll runs can be approved");
        }
        if ("PAID".equals(request.decision()) && !"APPROVED".equals(currentStatus)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Only approved payroll runs can be marked paid");
        }
        jdbc.sql("""
            update payroll_runs set status=:status,
              approved_by_subject=case when :approved=true then :actor else approved_by_subject end,
              approved_at=case when :approved=true then current_timestamp else approved_at end,
              paid_at=case when :paid=true then current_timestamp else paid_at end
            where id=:id and company_id=:companyId
            """).param("status", request.decision()).param("approved", "APPROVED".equals(request.decision()))
            .param("actor", actor).param("paid", "PAID".equals(request.decision()))
            .param("id", request.runId().toString()).param("companyId", companyId.toString()).update();
        audit.write(companyId, actor, "HR_PAYROLL_RUN_" + request.decision(), "PAYROLL_RUN", request.runId().toString(), "{}");
        return new CreatedResource(request.runId(), request.decision());
    }

    @Transactional
    public List<Map<String, Object>> publishPayslips(UUID companyId, PayslipPublishRequest request, String actor) {
        var run = jdbc.sql("""
            select id,status,period_start,period_end,currency from payroll_runs
            where id=:id and company_id=:companyId
            """).param("id", request.runId().toString()).param("companyId", companyId.toString())
            .query((rs, row) -> new PayrollRunStatus(rs.getString("id"), rs.getString("status"),
                rs.getObject("period_start", LocalDate.class), rs.getObject("period_end", LocalDate.class),
                rs.getString("currency")))
            .optional().orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Payroll run not found"));
        if (!"APPROVED".equals(run.status())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Payroll run must be approved before publishing payslips");
        }
        if (request.currency() != null && !request.currency().equals(run.currency())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Currency does not match the payroll run");
        }
        var templates = jdbc.sql("""
            select s.employee_id,s.base_salary_ciphertext,s.allowances_ciphertext,s.deductions_ciphertext
            from salary_structures s
            where s.company_id=:companyId and s.effective_from<=:periodStart
              and (s.effective_to is null or s.effective_to>=:periodStart)
            order by s.effective_from desc
            """).param("companyId", companyId.toString()).param("periodStart", run.periodStart())
            .query((rs, row) -> new SalaryTemplate(UUID.fromString(rs.getString("employee_id")),
                cipher.decrypt(rs.getString("base_salary_ciphertext")),
                cipher.decrypt(rs.getString("allowances_ciphertext")),
                cipher.decrypt(rs.getString("deductions_ciphertext"))))
            .list();
        var seen = new java.util.LinkedHashSet<UUID>();
        var result = new java.util.ArrayList<Map<String, Object>>();
        for (SalaryTemplate template : templates) {
            if (!seen.add(template.employeeId())) continue;
            java.math.BigDecimal base = template.base() == null ? java.math.BigDecimal.ZERO : new java.math.BigDecimal(template.base());
            java.math.BigDecimal allowances = template.allowances() == null ? java.math.BigDecimal.ZERO : new java.math.BigDecimal(template.allowances());
            java.math.BigDecimal deductions = template.deductions() == null ? java.math.BigDecimal.ZERO : new java.math.BigDecimal(template.deductions());
            java.math.BigDecimal gross = base.add(allowances);
            java.math.BigDecimal net = gross.subtract(deductions);
            if (net.compareTo(java.math.BigDecimal.ZERO) < 0) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Negative net salary for employee " + template.employeeId());
            }
            jdbc.sql("""
                delete from payslip_components where company_id=:companyId and payslip_id in (
                    select id from payslips where payroll_run_id=:runId and employee_id=:employeeId
                )
                """).param("companyId", companyId.toString()).param("runId", request.runId().toString())
                .param("employeeId", template.employeeId().toString()).update();
            jdbc.sql("""
                insert into payslips(id,company_id,payroll_run_id,employee_id,gross_ciphertext,deduction_ciphertext,net_ciphertext,published_at)
                values(:id,:companyId,:runId,:employeeId,:gross,:deduction,:net,current_timestamp)
                on conflict (payroll_run_id,employee_id) do update set gross_ciphertext=:gross,deduction_ciphertext=:deduction,net_ciphertext=:net,published_at=current_timestamp
                """).param("id", UUID.randomUUID().toString()).param("companyId", companyId.toString()).param("runId", request.runId().toString())
                .param("employeeId", template.employeeId().toString())
                .param("gross", cipher.encrypt(gross.toPlainString())).param("deduction", cipher.encrypt(deductions.toPlainString()))
                .param("net", cipher.encrypt(net.toPlainString())).update();
            UUID slipId = jdbc.sql("select id from payslips where payroll_run_id=:runId and employee_id=:employeeId")
                .param("runId", request.runId().toString()).param("employeeId", template.employeeId().toString())
                .query(String.class).optional().map(UUID::fromString)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Payslip was not created"));
            insertPaylipComponent(companyId, slipId, "GROSS", "BASE_SALARY", "Lương cơ bản", base, 1);
            insertPaylipComponent(companyId, slipId, "GROSS", "ALLOWANCES", "Phụ cấp", allowances, 2);
            insertPaylipComponent(companyId, slipId, "DEDUCTION", "DEDUCTIONS", "Khấu trừ", deductions, 3);
            result.add(Map.of("employeeId", template.employeeId(), "gross", gross.toPlainString(), "net", net.toPlainString()));
        }
        if (seen.size() > 0) {
            jdbc.sql("update payroll_runs set status='PAID', paid_at=current_timestamp where id=:id and company_id=:companyId and status='APPROVED'")
                .param("id", request.runId().toString()).param("companyId", companyId.toString()).update();
        }
        audit.write(companyId, actor, "HR_PAYSLIPS_PUBLISHED", "PAYROLL_RUN", request.runId().toString(), "{\"count\":" + result.size() + "}");
        return result;
    }

    public Map<String, Object> salaryStructure(UUID companyId, UUID employeeId) {
        ensureCompanyResource("employees", companyId, employeeId, "Employee");
        var rows = namedJdbc.queryForList("""
            select effective_from,effective_to,currency,base_salary_ciphertext,allowances_ciphertext,deductions_ciphertext,created_by_subject
            from salary_structures where company_id=:companyId and employee_id=:employeeId
            order by effective_from desc limit 1
            """, Map.of("companyId", companyId.toString(), "employeeId", employeeId.toString()));
        if (rows.isEmpty()) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Salary structure not found");
        Map<String, Object> row = rows.get(0);
        Map<String, Object> view = new java.util.LinkedHashMap<>();
        view.put("effectiveFrom", row.get("effective_from"));
        view.put("effectiveTo", row.get("effective_to"));
        view.put("currency", row.get("currency"));
        view.put("baseSalary", cipher.decrypt((String) row.get("base_salary_ciphertext")));
        view.put("allowances", cipher.decrypt((String) row.get("allowances_ciphertext")));
        view.put("deductions", cipher.decrypt((String) row.get("deductions_ciphertext")));
        view.put("createdBy", row.get("created_by_subject"));
        return view;
    }

    public List<Map<String, Object>> payrollAdjustmentHistory(UUID companyId, UUID runId) {
        return namedJdbc.queryForList("""
            select action,actor_subject,resource_id,metadata,created_at from audit_logs
            where company_id=:companyId and resource_type in ('PAYROLL_RUN','PAYSLIP','SALARY_STRUCTURE')
              and (resource_id=:runId or metadata like :runMarker)
            order by created_at desc limit 200
            """, Map.of("companyId", companyId.toString(), "runId", runId.toString(),
            "runMarker", "%" + runId + "%"));
    }

    // === Performance: reviews, feedback, objectives ===

    @Transactional
    public CreatedResource createReview(UUID companyId, PerformanceReviewRequest request, String actor) {
        ensureCompanyResource("performance_cycles", companyId, request.cycleId(), "Performance cycle");
        ensureCompanyResource("employees", companyId, request.employeeId(), "Employee");
        ensureCompanyResource("employees", companyId, request.reviewerEmployeeId(), "Reviewer");
        if (request.reviewerEmployeeId().equals(request.employeeId())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Reviewer must be different from the reviewed employee");
        }
        UUID id = UUID.randomUUID();
        jdbc.sql("""
            insert into performance_reviews(id,company_id,cycle_id,employee_id,reviewer_employee_id,review_type,status,
              score,strengths,improvements,development_plan)
            values(:id,:companyId,:cycleId,:employeeId,:reviewerId,:reviewType,'DRAFT',:score,:strengths,:improvements,:plan)
            """).param("id", id.toString()).param("companyId", companyId.toString()).param("cycleId", request.cycleId().toString())
            .param("employeeId", request.employeeId().toString()).param("reviewerId", request.reviewerEmployeeId().toString())
            .param("reviewType", request.reviewType()).param("score", request.score())
            .param("strengths", blankToNull(request.strengths())).param("improvements", blankToNull(request.improvements()))
            .param("plan", blankToNull(request.developmentPlan())).update();
        audit.write(companyId, actor, "HR_PERFORMANCE_REVIEW_CREATED", "PERFORMANCE_REVIEW", id.toString(), "{}");
        return new CreatedResource(id, "DRAFT");
    }

    @Transactional
    public CreatedResource decideReview(UUID companyId, PerformanceReviewDecisionRequest request, String actor) {
        ensureCompanyResource("performance_reviews", companyId, request.reviewId(), "Performance review");
        jdbc.sql("""
            update performance_reviews set status=:status,
              submitted_at=case when :submitted=true then current_timestamp else submitted_at end
            where id=:id and company_id=:companyId
            """).param("status", request.status()).param("submitted", "SUBMITTED".equals(request.status()))
            .param("id", request.reviewId().toString()).param("companyId", companyId.toString()).update();
        audit.write(companyId, actor, "HR_PERFORMANCE_REVIEW_" + request.status(), "PERFORMANCE_REVIEW", request.reviewId().toString(), "{}");
        return new CreatedResource(request.reviewId(), request.status());
    }

    @Transactional
    public CreatedResource addFeedback(UUID companyId, FeedbackRequest request, String actorSubject) {
        UUID authorId = jdbc.sql("select id from employees where company_id=:companyId and keycloak_subject=:subject")
            .param("companyId", companyId.toString()).param("subject", actorSubject).query(String.class).optional()
            .map(UUID::fromString).orElseThrow(() -> new ResponseStatusException(HttpStatus.FORBIDDEN, "Reviewer identity is not linked to an employee"));
        ensureCompanyResource("employees", companyId, request.employeeId(), "Employee");
        UUID id = UUID.randomUUID();
        jdbc.sql("""
            insert into employee_feedback(id,company_id,employee_id,author_employee_id,visibility,feedback)
            values(:id,:companyId,:employeeId,:authorId,:visibility,:feedback)
            """).param("id", id.toString()).param("companyId", companyId.toString()).param("employeeId", request.employeeId().toString())
            .param("authorId", authorId.toString()).param("visibility", request.visibility()).param("feedback", request.feedback().trim()).update();
        audit.write(companyId, actorSubject, "HR_FEEDBACK_ADDED", "EMPLOYEE_FEEDBACK", id.toString(), "{}");
        return new CreatedResource(id, "RECORDED");
    }

    @Transactional
    public CreatedResource updateObjectiveProgress(UUID companyId, UUID objectiveId, java.math.BigDecimal currentValue, String actor) {
        ensureCompanyResource("employee_objectives", companyId, objectiveId, "Objective");
        jdbc.sql("update employee_objectives set current_value=:value,status=case when :done=true then 'COMPLETED' else 'ACTIVE' end where id=:id and company_id=:companyId")
            .param("value", currentValue).param("done", isCompletedObjective(currentValue, companyId, objectiveId))
            .param("id", objectiveId.toString()).param("companyId", companyId.toString()).update();
        audit.write(companyId, actor, "HR_OBJECTIVE_UPDATED", "EMPLOYEE_OBJECTIVE", objectiveId.toString(), "{}");
        return new CreatedResource(objectiveId, "UPDATED");
    }

    // === Training: progress, certificates, skills ===

    @Transactional
    public CreatedResource updateEnrollmentProgress(UUID companyId, CourseProgressRequest request, String actor) {
        ensureCompanyResource("training_enrollments", companyId, request.enrollmentId(), "Training enrollment");
        boolean finished = request.progressPercent().compareTo(java.math.BigDecimal.valueOf(100)) == 0;
        jdbc.sql("""
            update training_enrollments set progress_percent=:progress, score=coalesce(:score,score),
              status=case when :finished=true then 'COMPLETED' else 'IN_PROGRESS' end,
              completed_at=case when :finished=true then current_timestamp else completed_at end
            where id=:id and company_id=:companyId
            """).param("progress", request.progressPercent()).param("score", request.score()).param("finished", finished)
            .param("id", request.enrollmentId().toString()).param("companyId", companyId.toString()).update();
        audit.write(companyId, actor, "HR_TRAINING_PROGRESS_UPDATED", "TRAINING_ENROLLMENT", request.enrollmentId().toString(), "{}");
        return new CreatedResource(request.enrollmentId(), finished ? "COMPLETED" : "IN_PROGRESS");
    }

    @Transactional
    public CreatedResource addCertificate(UUID companyId, CertificateRequest request, String actor) {
        ensureCompanyResource("employees", companyId, request.employeeId(), "Employee");
        UUID id = UUID.randomUUID();
        jdbc.sql("""
            insert into employee_certificates(id,company_id,employee_id,name,issuer,issued_on,expires_on,credential_id,document_object_key)
            values(:id,:companyId,:employeeId,:name,:issuer,:issuedOn,:expiresOn,:credentialId,:documentKey)
            """).param("id", id.toString()).param("companyId", companyId.toString()).param("employeeId", request.employeeId().toString())
            .param("name", request.name().trim()).param("issuer", blankToNull(request.issuer()))
            .param("issuedOn", request.issuedOn()).param("expiresOn", request.expiresOn())
            .param("credentialId", blankToNull(request.credentialId())).param("documentKey", blankToNull(request.documentObjectKey())).update();
        audit.write(companyId, actor, "HR_CERTIFICATE_ADDED", "EMPLOYEE_CERTIFICATE", id.toString(), "{}");
        return new CreatedResource(id, "RECORDED");
    }

    @Transactional
    public CreatedResource addSkill(UUID companyId, SkillRequest request, String actor) {
        ensureCompanyResource("employees", companyId, request.employeeId(), "Employee");
        if (request.verifiedByEmployeeId() != null) ensureCompanyResource("employees", companyId, request.verifiedByEmployeeId(), "Skill verifier");
        UUID id = UUID.randomUUID();
        jdbc.sql("""
            insert into employee_skills(id,company_id,employee_id,skill_name,proficiency,verified_by_employee_id,verified_at)
            values(:id,:companyId,:employeeId,:skill,:proficiency,:verifierId,:verifiedAt)
            """).param("id", id.toString()).param("companyId", companyId.toString()).param("employeeId", request.employeeId().toString())
            .param("skill", request.skillName().trim()).param("proficiency", request.proficiency())
            .param("verifierId", uuid(request.verifiedByEmployeeId()))
            .param("verifiedAt", request.verifiedByEmployeeId() == null ? null : new Timestamp(System.currentTimeMillis()))
            .update();
        audit.write(companyId, actor, "HR_SKILL_ADDED", "EMPLOYEE_SKILL", id.toString(), "{}");
        return new CreatedResource(id, "RECORDED");
    }

    // === Offboarding: case status transitions ===

    @Transactional
    public CreatedResource decideOffboarding(UUID companyId, OffboardingDecisionRequest request, String actor) {
        ensureCompanyResource("offboarding_cases", companyId, request.caseId(), "Offboarding case");
        boolean finished = "COMPLETED".equals(request.status());
        jdbc.sql("""
            update offboarding_cases set status=:status where id=:id and company_id=:companyId
            """).param("status", request.status()).param("id", request.caseId().toString()).param("companyId", companyId.toString()).update();
        if (finished) {
            jdbc.sql("update employees set employment_status='TERMINATED' where id=(select employee_id from offboarding_cases where id=:id and company_id=:companyId)")
                .param("id", request.caseId().toString()).param("companyId", companyId.toString()).update();
            jdbc.sql("update offboarding_cases set archive_after=(current_date + interval '7 years')::date where id=:id and company_id=:companyId")
                .param("id", request.caseId().toString()).param("companyId", companyId.toString()).update();
        }
        audit.write(companyId, actor, "HR_OFFBOARDING_" + request.status(), "OFFBOARDING_CASE", request.caseId().toString(), "{}");
        return new CreatedResource(request.caseId(), request.status());
    }

    // === Employee documents (hồ sơ đính kèm) ===

    @Transactional
    public CreatedResource addEmployeeDocument(UUID companyId, EmployeeDocumentRequest request, String actor) {
        ensureCompanyResource("employees", companyId, request.employeeId(), "Employee");
        UUID id = UUID.randomUUID();
        jdbc.sql("""
            insert into employee_documents(id,company_id,employee_id,document_type,display_name,object_key,classification,uploaded_by_subject)
            values(:id,:companyId,:employeeId,:type,:name,:objectKey,:classification,:actor)
            """).param("id", id.toString()).param("companyId", companyId.toString()).param("employeeId", request.employeeId().toString())
            .param("type", request.documentType().trim()).param("name", request.displayName().trim())
            .param("objectKey", request.objectKey().trim()).param("classification", request.classification()).param("actor", actor).update();
        audit.write(companyId, actor, "HR_EMPLOYEE_DOCUMENT_ADDED", "EMPLOYEE_DOCUMENT", id.toString(), "{}");
        return new CreatedResource(id, "PENDING");
    }

    public List<Map<String, Object>> employeeDocuments(UUID companyId, UUID employeeId) {
        ensureCompanyResource("employees", companyId, employeeId, "Employee");
        return namedJdbc.queryForList("""
            select id,document_type,display_name,classification,malware_scan_status,uploaded_by_subject,created_at
            from employee_documents where company_id=:companyId and employee_id=:employeeId order by created_at desc
            """, Map.of("companyId", companyId.toString(), "employeeId", employeeId.toString()));
    }

    // === Helpers ===

    private void updateCaseProgress(UUID companyId, UUID caseId, String table, String foreignKey) {
        long total = jdbc.sql("select count(*) from " + table + " where " + foreignKey + "=:caseId and company_id=:companyId")
            .param("caseId", caseId.toString()).param("companyId", companyId.toString()).query(Long.class).single();
        long done = jdbc.sql("select count(*) from " + table + " where " + foreignKey + "=:caseId and company_id=:companyId and status='COMPLETED'")
            .param("caseId", caseId.toString()).param("companyId", companyId.toString()).query(Long.class).single();
        double percent = total == 0 ? 0 : done * 100.0 / total;
        jdbc.sql("update " + (table.contains("onboarding") ? "onboarding_cases" : "offboarding_cases") +
            " set progress_percent=case when :pct>=100 then 100 else :pct end, status=case when :pct>=100 then 'COMPLETED' when status='COMPLETED' then 'IN_PROGRESS' else status end where id=:caseId and company_id=:companyId")
            .param("pct", percent).param("caseId", caseId.toString()).param("companyId", companyId.toString()).update();
    }

    private boolean isCompletedObjective(java.math.BigDecimal currentValue, UUID companyId, UUID objectiveId) {
        java.math.BigDecimal target = jdbc.sql("select target_value from employee_objectives where id=:id and company_id=:companyId")
            .param("id", objectiveId.toString()).param("companyId", companyId.toString()).query(java.math.BigDecimal.class).optional().orElse(null);
        return target != null && target.signum() > 0 && currentValue != null && currentValue.compareTo(target) >= 0;
    }

    private void insertPaylipComponent(UUID companyId, UUID payslipId, String componentType, String code, String name,
            java.math.BigDecimal amount, int order) {
        jdbc.sql("""
            insert into payslip_components(id,company_id,payslip_id,component_type,code,name,amount_ciphertext,display_order)
            values(:id,:companyId,:payslipId,:type,:code,:name,:amount,:order)
            """).param("id", UUID.randomUUID().toString()).param("companyId", companyId.toString()).param("payslipId", payslipId.toString())
            .param("type", componentType).param("code", code).param("name", name)
            .param("amount", cipher.encrypt(amount == null ? "0" : amount.toPlainString())).param("order", order).update();
    }

    private record PayrollRunStatus(String id, String status, LocalDate periodStart, LocalDate periodEnd, String currency) {}
    private record SalaryTemplate(UUID employeeId, String base, String allowances, String deductions) {}

    private EmployeeView employee(UUID companyId, UUID employeeId) {
        return jdbc.sql("""
            select e.id,e.employee_code,e.full_name,e.work_email,e.branch_id,b.name branch_name,
                   e.department_id,d.name department_name,e.position_id,p.title position_title,
                   e.job_title,e.manager_employee_id,e.join_date,e.employment_status
            from employees e left join company_branches b on b.id=e.branch_id
              left join departments d on d.id=e.department_id
              left join job_positions p on p.id=e.position_id
            where e.company_id=:companyId and e.id=:employeeId
            """).param("companyId", companyId.toString()).param("employeeId", employeeId.toString())
            .query(this::employeeView).optional().orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Employee not found"));
    }

    private EmployeeView employeeView(ResultSet rs, int row) throws SQLException {
        String branch = rs.getString("branch_id");
        String department = rs.getString("department_id");
        String position = rs.getString("position_id");
        String manager = rs.getString("manager_employee_id");
        return new EmployeeView(UUID.fromString(rs.getString("id")), rs.getString("employee_code"), rs.getString("full_name"),
            rs.getString("work_email"), branch == null ? null : UUID.fromString(branch), rs.getString("branch_name"),
            department == null ? null : UUID.fromString(department), rs.getString("department_name"),
            position == null ? null : UUID.fromString(position), rs.getString("position_title"), rs.getString("job_title"),
            manager == null ? null : UUID.fromString(manager),
            rs.getObject("join_date", LocalDate.class), rs.getString("employment_status"));
    }

    private void ensureEmployeeOrganization(UUID companyId, UUID branchId, UUID departmentId, UUID positionId) {
        if (branchId != null) ensureCompanyResource("company_branches", companyId, branchId, "Branch");
        if (departmentId != null) ensureCompanyResource("departments", companyId, departmentId, "Department");
        if (positionId != null) ensureCompanyResource("job_positions", companyId, positionId, "Position");
        if (branchId != null && departmentId != null) {
            long matches = jdbc.sql("""
                select count(*) from departments
                where company_id=:companyId and id=:departmentId and (branch_id is null or branch_id=:branchId)
                """).param("companyId", companyId.toString()).param("departmentId", departmentId.toString())
                .param("branchId", branchId.toString()).query(Long.class).single();
            if (matches == 0) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Department does not belong to branch");
        }
        if (departmentId != null && positionId != null) {
            long matches = jdbc.sql("""
                select count(*) from job_positions
                where company_id=:companyId and id=:positionId and (department_id is null or department_id=:departmentId)
                """).param("companyId", companyId.toString()).param("positionId", positionId.toString())
                .param("departmentId", departmentId.toString()).query(Long.class).single();
            if (matches == 0) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Position does not belong to department");
        }
    }

    private void savePrivate(UUID companyId, UUID employeeId, PrivateProfileRequest profile) {
        jdbc.sql("""
            insert into employee_private_profiles(employee_id,company_id,date_of_birth,identity_type,identity_number_ciphertext,
              personal_email_ciphertext,personal_phone_ciphertext,address_ciphertext,emergency_name_ciphertext,emergency_phone_ciphertext)
            values(:employeeId,:companyId,:dob,:identityType,:identityNumber,:personalEmail,:personalPhone,:address,:emergencyName,:emergencyPhone)
            """).param("employeeId", employeeId.toString()).param("companyId", companyId.toString())
            .param("dob", profile.dateOfBirth()).param("identityType", blankToNull(profile.identityType()))
            .param("identityNumber", cipher.encrypt(profile.identityNumber())).param("personalEmail", cipher.encrypt(profile.personalEmail()))
            .param("personalPhone", cipher.encrypt(profile.personalPhone())).param("address", cipher.encrypt(profile.address()))
            .param("emergencyName", cipher.encrypt(profile.emergencyName())).param("emergencyPhone", cipher.encrypt(profile.emergencyPhone())).update();
    }

    private long count(String table, String condition, UUID companyId) {
        return jdbc.sql("select count(*) from " + table + " where company_id=:companyId and " + condition)
            .param("companyId", companyId.toString()).query(Long.class).single();
    }
    private List<Map<String, Object>> rows(String sql, UUID companyId) {
        return namedJdbc.queryForList(sql, Map.of("companyId", companyId.toString()));
    }
    private UUID ownEmployeeId(UUID companyId, String subject) {
        return jdbc.sql("select id from employees where company_id=:companyId and keycloak_subject=:subject and employment_status in ('ACTIVE','PROBATION')")
            .param("companyId", companyId.toString()).param("subject", subject).query(String.class).optional()
            .map(UUID::fromString).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Employee identity is not linked"));
    }
    private void ensureCompanyResource(String table, UUID companyId, UUID id, String label) {
        long found = jdbc.sql("select count(*) from " + table + " where company_id=:companyId and id=:id")
            .param("companyId", companyId.toString()).param("id", id.toString()).query(Long.class).single();
        if (found == 0) throw new ResponseStatusException(HttpStatus.NOT_FOUND, label + " not found");
    }
    private void createChecklist(UUID companyId, UUID caseId, String table, String foreignKey,
            List<ChecklistItem> items, LocalDate dueOn) {
        for (ChecklistItem item : items) {
            jdbc.sql("insert into " + table + "(id,company_id," + foreignKey + ",category,title,due_on,status) " +
                "values(:id,:companyId,:caseId,:category,:title,:dueOn,'PENDING')")
                .param("id", UUID.randomUUID().toString()).param("companyId", companyId.toString())
                .param("caseId", caseId.toString()).param("category", item.category()).param("title", item.title())
                .param("dueOn", dueOn).update();
        }
    }
    private static String blankToNull(String value) { return value == null || value.isBlank() ? null : value.trim(); }
    private static String uuid(UUID value) { return value == null ? null : value.toString(); }
    private static String decimal(java.math.BigDecimal value) { return value == null ? null : value.toPlainString(); }
    private static Timestamp timestamp(java.time.OffsetDateTime value) { return value == null ? null : Timestamp.from(value.toInstant()); }
    private static Instant instant(Timestamp value) { return value == null ? null : value.toInstant(); }
    private static boolean allowed(Authentication authentication, String permission) {
        return authentication.getAuthorities().stream().anyMatch(authority -> authority.getAuthority().equals(permission)
            || authority.getAuthority().equals("super-admin") || authority.getAuthority().equals("ROLE_SUPER_ADMIN"));
    }
    private record Adjustment(UUID entryId, Timestamp checkIn, Timestamp checkOut) {}
    private record LeaveReview(UUID employeeId, UUID leaveTypeId, int year, java.math.BigDecimal days) {}
    private record ChecklistItem(String category, String title) {}
}
