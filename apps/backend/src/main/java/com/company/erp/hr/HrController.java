package com.company.erp.hr;

import com.company.erp.hr.HrModels.EmployeeCreateRequest;
import com.company.erp.hr.HrModels.EmployeeView;
import com.company.erp.hr.HrModels.HrSummary;
import com.company.erp.hr.HrModels.PayslipView;
import com.company.erp.hr.HrModels.PrivateProfileView;
import com.company.erp.hr.HrModels.*;
import jakarta.validation.Valid;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/hr")
public class HrController {
    private final HrService hr;

    public HrController(HrService hr) { this.hr = hr; }

    @GetMapping("/summary")
    @PreAuthorize("@companyAccess.canAccess(authentication,#companyId,'hr.access')")
    HrSummary summary(@RequestParam UUID companyId, Authentication authentication) { return hr.summary(companyId, authentication); }

    @GetMapping("/employees")
    @PreAuthorize("@companyAccess.canAccess(authentication,#companyId,'hr.employee.read.basic')")
    List<EmployeeView> employees(@RequestParam UUID companyId) { return hr.employees(companyId); }

    @GetMapping("/employees/me")
    @PreAuthorize("@companyAccess.canAccess(authentication,#companyId,'hr.access')")
    EmployeeView ownEmployee(@RequestParam UUID companyId, Authentication authentication) {
        return hr.ownEmployee(companyId, authentication.getName());
    }

    @PostMapping("/employees")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("@companyAccess.canAccess(authentication,#companyId,'hr.employee.write')")
    EmployeeView createEmployee(@RequestParam UUID companyId, @Valid @RequestBody EmployeeCreateRequest request,
            Authentication authentication) {
        return hr.createEmployee(companyId, request, authentication.getName());
    }

    @PatchMapping("/employees/{employeeId}")
    @PreAuthorize("@companyAccess.canAccess(authentication,#companyId,'hr.employee.write')")
    EmployeeView updateEmployee(@RequestParam UUID companyId, @PathVariable UUID employeeId,
            @Valid @RequestBody EmployeeUpdateRequest request, Authentication authentication) {
        return hr.updateEmployee(companyId, employeeId, request, authentication.getName());
    }

    @PatchMapping("/employees/{employeeId}/private")
    @PreAuthorize("@companyAccess.canAccess(authentication,#companyId,'hr.employee.write')")
    PrivateProfileView updatePrivateProfile(@RequestParam UUID companyId, @PathVariable UUID employeeId,
            @Valid @RequestBody PrivateProfileUpdateRequest request, Authentication authentication) {
        return hr.updatePrivateProfile(companyId, employeeId, request, authentication.getName());
    }

    @GetMapping("/employees/{employeeId}/private")
    @PreAuthorize("@companyAccess.canAccess(authentication,#companyId,'hr.employee.read.sensitive')")
    PrivateProfileView privateProfile(@RequestParam UUID companyId, @PathVariable UUID employeeId) {
        return hr.privateProfile(companyId, employeeId);
    }

    @GetMapping("/recruitment")
    @PreAuthorize("@companyAccess.canAccess(authentication,#companyId,'hr.recruitment.read')")
    List<Map<String, Object>> recruitment(@RequestParam UUID companyId) { return hr.recruitment(companyId); }

    @GetMapping("/recruitment/candidates")
    @PreAuthorize("@companyAccess.canAccess(authentication,#companyId,'hr.recruitment.read')")
    List<Map<String, Object>> candidates(@RequestParam UUID companyId) { return hr.recruitmentCandidates(companyId); }

    @GetMapping("/recruitment/interviews")
    @PreAuthorize("@companyAccess.canAccess(authentication,#companyId,'hr.recruitment.read')")
    List<Map<String, Object>> interviews(@RequestParam UUID companyId) { return hr.recruitmentInterviews(companyId); }

    @GetMapping("/recruitment/offers")
    @PreAuthorize("@companyAccess.canAccess(authentication,#companyId,'hr.recruitment.read')")
    List<Map<String, Object>> offers(@RequestParam UUID companyId) { return hr.recruitmentOffers(companyId); }

    @GetMapping("/onboarding/cases/{caseId}/tasks")
    @PreAuthorize("@companyAccess.canAccess(authentication,#companyId,'hr.onboarding.read')")
    List<Map<String, Object>> onboardingChecklist(@RequestParam UUID companyId, @PathVariable UUID caseId) {
        return hr.onboardingChecklist(companyId, caseId);
    }

    @GetMapping("/offboarding/cases/{caseId}/tasks")
    @PreAuthorize("@companyAccess.canAccess(authentication,#companyId,'hr.offboarding.read')")
    List<Map<String, Object>> offboardingChecklist(@RequestParam UUID companyId, @PathVariable UUID caseId) {
        return hr.offboardingChecklist(companyId, caseId);
    }

    @GetMapping("/performance/cycles")
    @PreAuthorize("@companyAccess.canAccess(authentication,#companyId,'hr.performance.read')")
    List<Map<String, Object>> performanceCycles(@RequestParam UUID companyId) { return hr.performanceCycles(companyId); }

    @PostMapping("/recruitment/positions")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("@companyAccess.canAccess(authentication,#companyId,'hr.recruitment.write')")
    CreatedResource createPosition(@RequestParam UUID companyId, @Valid @RequestBody RecruitmentPositionRequest request,
            Authentication authentication) { return hr.createPosition(companyId, request, authentication.getName()); }

    @PostMapping("/recruitment/candidates")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("@companyAccess.canAccess(authentication,#companyId,'hr.recruitment.write')")
    CreatedResource createCandidate(@RequestParam UUID companyId, @Valid @RequestBody CandidateRequest request,
            Authentication authentication) { return hr.createCandidate(companyId, request, authentication.getName()); }

    @PostMapping("/recruitment/interviews")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("@companyAccess.canAccess(authentication,#companyId,'hr.recruitment.write')")
    CreatedResource scheduleInterview(@RequestParam UUID companyId, @Valid @RequestBody InterviewRequest request,
            Authentication authentication) { return hr.scheduleInterview(companyId, request, authentication.getName()); }

    @GetMapping("/onboarding")
    @PreAuthorize("@companyAccess.canAccess(authentication,#companyId,'hr.onboarding.read')")
    List<Map<String, Object>> onboarding(@RequestParam UUID companyId) { return hr.onboarding(companyId); }

    @PostMapping("/onboarding")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("@companyAccess.canAccess(authentication,#companyId,'hr.onboarding.write')")
    CreatedResource createOnboarding(@RequestParam UUID companyId, @Valid @RequestBody OnboardingRequest request,
            Authentication authentication) { return hr.createOnboarding(companyId, request, authentication.getName()); }

    @GetMapping("/attendance")
    @PreAuthorize("@companyAccess.canAccess(authentication,#companyId,'hr.attendance.read')")
    List<Map<String, Object>> attendance(@RequestParam UUID companyId) { return hr.attendance(companyId); }

    @PostMapping("/attendance/check-in")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("@companyAccess.canAccess(authentication,#companyId,'hr.attendance.write')")
    CreatedResource checkIn(@RequestParam UUID companyId, Authentication authentication) {
        return hr.checkIn(companyId, authentication.getName());
    }

    @PostMapping("/attendance/check-out")
    @PreAuthorize("@companyAccess.canAccess(authentication,#companyId,'hr.attendance.write')")
    CreatedResource checkOut(@RequestParam UUID companyId, Authentication authentication) {
        return hr.checkOut(companyId, authentication.getName());
    }

    @PostMapping("/attendance/adjustments")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("@companyAccess.canAccess(authentication,#companyId,'hr.attendance.write')")
    CreatedResource requestAttendanceAdjustment(@RequestParam UUID companyId,
            @Valid @RequestBody AttendanceAdjustmentRequest request, Authentication authentication) {
        return hr.requestAttendanceAdjustment(companyId, request, authentication.getName());
    }

    @PostMapping("/attendance/adjustments/{requestId}/review")
    @PreAuthorize("@companyAccess.canAccess(authentication,#companyId,'hr.attendance.approve')")
    CreatedResource reviewAttendanceAdjustment(@RequestParam UUID companyId, @PathVariable UUID requestId,
            @Valid @RequestBody ReviewRequest request, Authentication authentication) {
        return hr.reviewAttendanceAdjustment(companyId, requestId, request, authentication.getName());
    }

    @GetMapping("/leave")
    @PreAuthorize("@companyAccess.canAccess(authentication,#companyId,'hr.leave.read')")
    List<Map<String, Object>> leave(@RequestParam UUID companyId) { return hr.leaveRequests(companyId); }

    @GetMapping("/leave/types")
    @PreAuthorize("@companyAccess.canAccess(authentication,#companyId,'hr.leave.read')")
    List<Map<String, Object>> leaveTypes(@RequestParam UUID companyId) { return hr.leaveTypes(companyId); }

    @GetMapping("/leave/balances/me")
    @PreAuthorize("@companyAccess.canAccess(authentication,#companyId,'hr.leave.read')")
    List<Map<String, Object>> ownLeaveBalances(@RequestParam UUID companyId, Authentication authentication) {
        return hr.ownLeaveBalances(companyId, authentication.getName());
    }

    @PostMapping("/leave")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("@companyAccess.canAccess(authentication,#companyId,'hr.leave.write')")
    CreatedResource requestLeave(@RequestParam UUID companyId, @Valid @RequestBody LeaveRequest request,
            Authentication authentication) { return hr.requestLeave(companyId, request, authentication.getName()); }

    @PostMapping("/leave/{requestId}/review")
    @PreAuthorize("@companyAccess.canAccess(authentication,#companyId,'hr.leave.approve')")
    CreatedResource reviewLeave(@RequestParam UUID companyId, @PathVariable UUID requestId,
            @Valid @RequestBody ReviewRequest request, Authentication authentication) {
        return hr.reviewLeave(companyId, requestId, request, authentication.getName());
    }

    @GetMapping("/payroll/runs")
    @PreAuthorize("@companyAccess.canAccess(authentication,#companyId,'hr.payroll.read.all')")
    List<Map<String, Object>> payrollRuns(@RequestParam UUID companyId) { return hr.payrollRuns(companyId); }

    @PostMapping("/payroll/salary-structures")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("@companyAccess.canAccess(authentication,#companyId,'hr.payroll.write')")
    CreatedResource createSalaryStructure(@RequestParam UUID companyId,
            @Valid @RequestBody SalaryStructureRequest request, Authentication authentication) {
        return hr.createSalaryStructure(companyId, request, authentication.getName());
    }

    @PostMapping("/payroll/runs")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("@companyAccess.canAccess(authentication,#companyId,'hr.payroll.write')")
    CreatedResource createPayrollRun(@RequestParam UUID companyId, @Valid @RequestBody PayrollRunRequest request,
            Authentication authentication) { return hr.createPayrollRun(companyId, request, authentication.getName()); }

    @GetMapping("/payroll/payslips")
    @PreAuthorize("@hrAccess.canViewPayslips(authentication,#companyId,#employeeId)")
    List<PayslipView> payslips(@RequestParam UUID companyId, @RequestParam UUID employeeId) {
        return hr.payslips(companyId, employeeId);
    }

    @GetMapping("/performance")
    @PreAuthorize("@companyAccess.canAccess(authentication,#companyId,'hr.performance.read')")
    List<Map<String, Object>> performance(@RequestParam UUID companyId) { return hr.performance(companyId); }

    @PostMapping("/performance/cycles")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("@companyAccess.canAccess(authentication,#companyId,'hr.performance.write')")
    CreatedResource createPerformanceCycle(@RequestParam UUID companyId,
            @Valid @RequestBody PerformanceCycleRequest request, Authentication authentication) {
        return hr.createPerformanceCycle(companyId, request, authentication.getName());
    }

    @PostMapping("/performance/objectives")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("@companyAccess.canAccess(authentication,#companyId,'hr.performance.write')")
    CreatedResource createObjective(@RequestParam UUID companyId, @Valid @RequestBody ObjectiveRequest request,
            Authentication authentication) { return hr.createObjective(companyId, request, authentication.getName()); }

    @GetMapping("/training/courses")
    @PreAuthorize("@companyAccess.canAccess(authentication,#companyId,'hr.training.read')")
    List<Map<String, Object>> trainingCourses(@RequestParam UUID companyId) { return hr.trainingCourses(companyId); }

    @GetMapping("/training")
    @PreAuthorize("@companyAccess.canAccess(authentication,#companyId,'hr.training.read')")
    List<Map<String, Object>> training(@RequestParam UUID companyId) { return hr.training(companyId); }

    @PostMapping("/training/courses")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("@companyAccess.canAccess(authentication,#companyId,'hr.training.write')")
    CreatedResource createCourse(@RequestParam UUID companyId, @Valid @RequestBody TrainingCourseRequest request,
            Authentication authentication) { return hr.createCourse(companyId, request, authentication.getName()); }

    @PostMapping("/training/enrollments")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("@companyAccess.canAccess(authentication,#companyId,'hr.training.write')")
    CreatedResource enrollTraining(@RequestParam UUID companyId,
            @Valid @RequestBody TrainingEnrollmentRequest request, Authentication authentication) {
        return hr.enrollTraining(companyId, request, authentication.getName());
    }

    @GetMapping("/offboarding")
    @PreAuthorize("@companyAccess.canAccess(authentication,#companyId,'hr.offboarding.read')")
    List<Map<String, Object>> offboarding(@RequestParam UUID companyId) { return hr.offboarding(companyId); }
    @PostMapping("/recruitment/candidates/{candidateId}/stage")
    @PreAuthorize("@companyAccess.canAccess(authentication,#companyId,'hr.recruitment.write')")
    CreatedResource moveCandidate(@RequestParam UUID companyId, @PathVariable UUID candidateId,
            @Valid @RequestBody CandidateStageRequest body, Authentication authentication) {
        return hr.moveCandidate(companyId, new CandidateStageRequest(candidateId, body.stage()), authentication.getName());
    }

    @PostMapping("/recruitment/interviews/{interviewId}/evaluate")
    @PreAuthorize("@companyAccess.canAccess(authentication,#companyId,'hr.recruitment.write')")
    CreatedResource evaluateInterview(@RequestParam UUID companyId, @PathVariable UUID interviewId,
            @Valid @RequestBody InterviewReviewRequest body, Authentication authentication) {
        return hr.evaluateInterview(companyId,
            new InterviewReviewRequest(interviewId, body.score(), body.recommendation(), body.feedback()), authentication.getName());
    }

    @PostMapping("/recruitment/offers")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("@companyAccess.canAccess(authentication,#companyId,'hr.recruitment.write')")
    CreatedResource createOffer(@RequestParam UUID companyId, @Valid @RequestBody OfferRequest request,
            Authentication authentication) { return hr.createOffer(companyId, request, authentication.getName()); }

    @PostMapping("/recruitment/offers/{offerId}/decision")
    @PreAuthorize("@companyAccess.canAccess(authentication,#companyId,'hr.recruitment.write')")
    CreatedResource decideOffer(@RequestParam UUID companyId, @PathVariable UUID offerId,
            @Valid @RequestBody OfferDecisionRequest body, Authentication authentication) {
        return hr.decideOffer(companyId, new OfferDecisionRequest(offerId, body.decision()), authentication.getName());
    }

    @PostMapping("/onboarding/tasks")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("@companyAccess.canAccess(authentication,#companyId,'hr.onboarding.write')")
    CreatedResource addOnboardingTask(@RequestParam UUID companyId, @Valid @RequestBody OnboardingTaskRequest request,
            Authentication authentication) { return hr.addOnboardingTask(companyId, request, authentication.getName()); }

    @PostMapping("/onboarding/tasks/{taskId}/decision")
    @PreAuthorize("@companyAccess.canAccess(authentication,#companyId,'hr.onboarding.write')")
    CreatedResource completeOnboardingTask(@RequestParam UUID companyId, @PathVariable UUID taskId,
            @Valid @RequestBody OnboardingTaskDecisionRequest body, Authentication authentication) {
        return hr.completeOnboardingTask(companyId, new OnboardingTaskDecisionRequest(taskId, body.decision(), body.note()), authentication.getName());
    }

    @GetMapping("/attendance/adjustments")
    @PreAuthorize("@companyAccess.canAccess(authentication,#companyId,'hr.attendance.approve')")
    List<Map<String, Object>> adjustmentRequests(@RequestParam UUID companyId) { return hr.adjustmentRequests(companyId); }

    @GetMapping("/attendance/shifts")
    @PreAuthorize("@companyAccess.canAccess(authentication,#companyId,'hr.attendance.read')")
    List<Map<String, Object>> shifts(@RequestParam UUID companyId) { return hr.shifts(companyId); }

    @PostMapping("/attendance/shifts")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("@companyAccess.canAccess(authentication,#companyId,'hr.attendance.write')")
    CreatedResource createShift(@RequestParam UUID companyId, @Valid @RequestBody WorkShiftRequest request,
            Authentication authentication) { return hr.createShift(companyId, request, authentication.getName()); }

    @GetMapping("/attendance/history")
    @PreAuthorize("@companyAccess.canAccess(authentication,#companyId,'hr.attendance.read')")
    List<Map<String, Object>> attendanceHistory(@RequestParam UUID companyId,
            @RequestParam(required = false) UUID employeeId,
            @RequestParam(required = false) LocalDate from, @RequestParam(required = false) LocalDate to) {
        return hr.attendanceHistory(companyId, employeeId == null ? null : employeeId.toString(), from, to);
    }

    @GetMapping("/leave/calendar")
    @PreAuthorize("@companyAccess.canAccess(authentication,#companyId,'hr.leave.read')")
    List<Map<String, Object>> leaveCalendar(@RequestParam UUID companyId,
            @RequestParam LocalDate from, @RequestParam LocalDate to) {
        return hr.leaveCalendar(companyId, from, to);
    }

    @PostMapping("/payroll/runs/{runId}/decision")
    @PreAuthorize("@companyAccess.canAccess(authentication,#companyId,'hr.payroll.write')")
    CreatedResource decidePayrollRun(@RequestParam UUID companyId, @PathVariable UUID runId,
            @Valid @RequestBody PayrollRunDecisionRequest body, Authentication authentication) {
        return hr.decidePayrollRun(companyId, new PayrollRunDecisionRequest(runId, body.decision()), authentication.getName());
    }

    @PostMapping("/payroll/runs/{runId}/publish-payslips")
    @PreAuthorize("@companyAccess.canAccess(authentication,#companyId,'hr.payroll.write')")
    List<Map<String, Object>> publishPayslips(@RequestParam UUID companyId, @PathVariable UUID runId,
            @Valid @RequestBody PayslipPublishRequest body, Authentication authentication) {
        return hr.publishPayslips(companyId, new PayslipPublishRequest(runId, body.currency()), authentication.getName());
    }

    @GetMapping("/payroll/salary-structures/{employeeId}")
    @PreAuthorize("@companyAccess.canAccess(authentication,#companyId,'hr.payroll.read.all')")
    Map<String, Object> salaryStructure(@RequestParam UUID companyId, @PathVariable UUID employeeId) {
        return hr.salaryStructure(companyId, employeeId);
    }

    @GetMapping("/payroll/runs/{runId}/history")
    @PreAuthorize("@companyAccess.canAccess(authentication,#companyId,'hr.payroll.read.all')")
    List<Map<String, Object>> payrollHistory(@RequestParam UUID companyId, @PathVariable UUID runId) {
        return hr.payrollAdjustmentHistory(companyId, runId);
    }

    @PostMapping("/performance/reviews")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("@companyAccess.canAccess(authentication,#companyId,'hr.performance.write')")
    CreatedResource createReview(@RequestParam UUID companyId, @Valid @RequestBody PerformanceReviewRequest request,
            Authentication authentication) { return hr.createReview(companyId, request, authentication.getName()); }

    @PostMapping("/performance/reviews/{reviewId}/decision")
    @PreAuthorize("@companyAccess.canAccess(authentication,#companyId,'hr.performance.write')")
    CreatedResource decideReview(@RequestParam UUID companyId, @PathVariable UUID reviewId,
            @Valid @RequestBody PerformanceReviewDecisionRequest body, Authentication authentication) {
        return hr.decideReview(companyId, new PerformanceReviewDecisionRequest(reviewId, body.status()), authentication.getName());
    }

    @PostMapping("/performance/feedback")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("@companyAccess.canAccess(authentication,#companyId,'hr.performance.write')")
    CreatedResource addFeedback(@RequestParam UUID companyId, @Valid @RequestBody FeedbackRequest request,
            Authentication authentication) { return hr.addFeedback(companyId, request, authentication.getName()); }

    @PostMapping("/performance/objectives/{objectiveId}/progress")
    @PreAuthorize("@companyAccess.canAccess(authentication,#companyId,'hr.performance.write')")
    CreatedResource updateObjectiveProgress(@RequestParam UUID companyId, @PathVariable UUID objectiveId,
            @Valid @RequestBody ObjectiveProgressRequest request, Authentication authentication) {
        return hr.updateObjectiveProgress(companyId, objectiveId, request.currentValue(), authentication.getName());
    }

    @PostMapping("/training/enrollments/{enrollmentId}/progress")
    @PreAuthorize("@companyAccess.canAccess(authentication,#companyId,'hr.training.write')")
    CreatedResource updateEnrollmentProgress(@RequestParam UUID companyId, @PathVariable UUID enrollmentId,
            @Valid @RequestBody CourseProgressRequest body, Authentication authentication) {
        return hr.updateEnrollmentProgress(companyId,
            new CourseProgressRequest(enrollmentId, body.progressPercent(), body.score()), authentication.getName());
    }

    @PostMapping("/training/certificates")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("@companyAccess.canAccess(authentication,#companyId,'hr.training.write')")
    CreatedResource addCertificate(@RequestParam UUID companyId, @Valid @RequestBody CertificateRequest request,
            Authentication authentication) { return hr.addCertificate(companyId, request, authentication.getName()); }

    @PostMapping("/training/skills")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("@companyAccess.canAccess(authentication,#companyId,'hr.training.write')")
    CreatedResource addSkill(@RequestParam UUID companyId, @Valid @RequestBody SkillRequest request,
            Authentication authentication) { return hr.addSkill(companyId, request, authentication.getName()); }

    @PostMapping("/offboarding/tasks/{taskId}/decision")
    @PreAuthorize("@companyAccess.canAccess(authentication,#companyId,'hr.offboarding.write')")
    CreatedResource completeOffboardingTask(@RequestParam UUID companyId, @PathVariable UUID taskId,
            @Valid @RequestBody OffboardingTaskDecisionRequest body, Authentication authentication) {
        return hr.completeOffboardingTask(companyId, new OffboardingTaskDecisionRequest(taskId, body.decision(), body.note()), authentication.getName());
    }

    @PostMapping("/offboarding/{caseId}/decision")
    @PreAuthorize("@companyAccess.canAccess(authentication,#companyId,'hr.offboarding.write')")
    CreatedResource decideOffboarding(@RequestParam UUID companyId, @PathVariable UUID caseId,
            @Valid @RequestBody OffboardingDecisionRequest body, Authentication authentication) {
        return hr.decideOffboarding(companyId, new OffboardingDecisionRequest(caseId, body.status()), authentication.getName());
    }

    @PostMapping("/employees/documents")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("@companyAccess.canAccess(authentication,#companyId,'hr.employee.write')")
    CreatedResource addEmployeeDocument(@RequestParam UUID companyId, @Valid @RequestBody EmployeeDocumentRequest request,
            Authentication authentication) { return hr.addEmployeeDocument(companyId, request, authentication.getName()); }

    @GetMapping("/employees/{employeeId}/documents")
    @PreAuthorize("@companyAccess.canAccess(authentication,#companyId,'hr.employee.read.sensitive')")
    List<Map<String, Object>> employeeDocuments(@RequestParam UUID companyId, @PathVariable UUID employeeId) {
        return hr.employeeDocuments(companyId, employeeId);
    }


    @PostMapping("/offboarding")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("@companyAccess.canAccess(authentication,#companyId,'hr.offboarding.write')")
    CreatedResource createOffboarding(@RequestParam UUID companyId, @Valid @RequestBody OffboardingRequest request,
            Authentication authentication) { return hr.createOffboarding(companyId, request, authentication.getName()); }
}
