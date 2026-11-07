package com.company.erp.hr;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.UUID;

public final class HrModels {
    private HrModels() {}

    public record EmployeeCreateRequest(
        @NotBlank @Size(max = 40) String employeeCode,
        @NotBlank @Size(max = 180) String fullName,
        @Email @Size(max = 254) String workEmail,
        UUID departmentId,
        @NotBlank @Size(max = 180) String jobTitle,
        UUID managerEmployeeId,
        @NotNull LocalDate joinDate,
        @Size(max = 100) String keycloakSubject,
        @Valid PrivateProfileRequest privateProfile) {}

    public record PrivateProfileRequest(
        LocalDate dateOfBirth,
        @Size(max = 40) String identityType,
        @Size(max = 255) String identityNumber,
        @Email @Size(max = 254) String personalEmail,
        @Size(max = 80) String personalPhone,
        @Size(max = 1000) String address,
        @Size(max = 180) String emergencyName,
        @Size(max = 80) String emergencyPhone) {}

    public record EmployeeView(UUID id, String employeeCode, String fullName, String workEmail,
        UUID departmentId, String departmentName, String jobTitle, UUID managerEmployeeId,
        LocalDate joinDate, String employmentStatus) {}

    public record PrivateProfileView(LocalDate dateOfBirth, String identityType, String identityNumber,
        String personalEmail, String personalPhone, String address, String emergencyName, String emergencyPhone) {}

    public record PayslipView(UUID id, UUID payrollRunId, UUID employeeId, LocalDate periodStart,
        LocalDate periodEnd, String currency, String gross, String deductions, String net,
        String payslipObjectKey, Instant publishedAt) {}

    public record HrSummary(Long employees, Long openPositions, Long activeCandidates, Long onboarding,
        Long attendanceToday, Long pendingLeave, Long draftPayrollRuns, Long openReviews,
        Long trainingInProgress, Long offboarding) {}

    public record RecruitmentPositionRequest(
        @NotBlank @Size(max = 50) String code,
        @NotBlank @Size(max = 180) String title,
        UUID departmentId,
        UUID hiringManagerEmployeeId,
        @NotNull Integer openings,
        @Size(max = 4000) String description,
        LocalDate closesOn) {}

    public record CandidateRequest(
        @NotNull UUID positionId,
        @NotBlank @Size(max = 180) String fullName,
        @NotBlank @Email @Size(max = 254) String email,
        @Size(max = 80) String phone,
        @Size(max = 500) String cvObjectKey,
        @Size(max = 80) String source) {}

    public record InterviewRequest(
        @NotNull UUID candidateId,
        @NotBlank @Size(max = 100) String roundName,
        @NotNull OffsetDateTime scheduledAt,
        @NotNull Integer durationMinutes,
        @Size(max = 500) String locationOrUrl) {}

    public record OnboardingRequest(@NotNull UUID employeeId, @NotNull LocalDate startDate, UUID mentorEmployeeId) {}

    public record AttendanceAdjustmentRequest(
        @NotNull UUID attendanceEntryId,
        OffsetDateTime proposedCheckIn,
        OffsetDateTime proposedCheckOut,
        @NotBlank @Size(max = 500) String reason) {}

    public record ReviewRequest(
        @NotBlank @jakarta.validation.constraints.Pattern(regexp = "APPROVED|REJECTED") String decision,
        @Size(max = 500) String note) {}

    public record LeaveRequest(
        @NotNull UUID leaveTypeId,
        @NotNull LocalDate startsOn,
        @NotNull LocalDate endsOn,
        @NotNull @jakarta.validation.constraints.DecimalMin("0.5") BigDecimal requestedDays,
        @Size(max = 500) String reason,
        @Size(max = 500) String documentObjectKey) {}

    public record SalaryStructureRequest(
        @NotNull UUID employeeId,
        @NotNull LocalDate effectiveFrom,
        LocalDate effectiveTo,
        @NotBlank @jakarta.validation.constraints.Pattern(regexp = "[A-Z]{3}") String currency,
        @NotNull @jakarta.validation.constraints.DecimalMin("0") BigDecimal baseSalary,
        @jakarta.validation.constraints.DecimalMin("0") BigDecimal allowances,
        @jakarta.validation.constraints.DecimalMin("0") BigDecimal deductions) {}

    public record PayrollRunRequest(
        @NotNull LocalDate periodStart,
        @NotNull LocalDate periodEnd,
        @NotBlank @jakarta.validation.constraints.Pattern(regexp = "[A-Z]{3}") String currency) {}

    public record PerformanceCycleRequest(
        @NotBlank @Size(max = 180) String name,
        @NotBlank @Size(max = 30) String cycleType,
        @NotNull LocalDate startsOn,
        @NotNull LocalDate endsOn) {}

    public record ObjectiveRequest(
        @NotNull UUID cycleId,
        @NotNull UUID employeeId,
        @NotBlank @jakarta.validation.constraints.Pattern(regexp = "KPI|OKR") String objectiveType,
        @NotBlank @Size(max = 255) String title,
        @Size(max = 4000) String description,
        BigDecimal targetValue,
        BigDecimal weight) {}

    public record TrainingCourseRequest(
        @NotBlank @Size(max = 50) String code,
        @NotBlank @Size(max = 255) String title,
        @Size(max = 4000) String description,
        @NotBlank @Size(max = 30) String deliveryType,
        BigDecimal passingScore) {}

    public record TrainingEnrollmentRequest(@NotNull UUID courseId, @NotNull UUID employeeId) {}

    public record OffboardingRequest(
        @NotNull UUID employeeId,
        LocalDate resignationSubmittedOn,
        @NotNull LocalDate lastWorkingDay,
        @Size(max = 60) String reasonCategory,
        @Size(max = 2000) String reason) {}


    public record InterviewReviewRequest(
        UUID interviewId,
        @NotNull @jakarta.validation.constraints.DecimalMin("0") @jakarta.validation.constraints.DecimalMax("100") BigDecimal score,
        @NotBlank @jakarta.validation.constraints.Pattern(regexp = "ADVANCE|HOLD|REJECT") String recommendation,
        @Size(max = 4000) String feedback) {}

    public record CandidateStageRequest(UUID candidateId, @NotBlank @jakarta.validation.constraints.Pattern(regexp = "APPLIED|SCREENING|INTERVIEW|OFFER|HIRED|WITHDRAWN|REJECTED") String stage) {}

    public record OfferRequest(
        @NotNull UUID candidateId,
        @NotBlank @Size(max = 180) String proposedTitle,
        LocalDate proposedStartDate,
        BigDecimal compensation) {}

    public record OfferDecisionRequest(UUID offerId, @NotBlank @jakarta.validation.constraints.Pattern(regexp = "ACCEPTED|DECLINED") String decision) {}

    public record OnboardingTaskRequest(
        @NotNull UUID onboardingCaseId,
        @NotBlank @Size(max = 40) String category,
        @NotBlank @Size(max = 255) String title,
        String assigneeSubject,
        LocalDate dueOn) {}

    public record OnboardingTaskDecisionRequest(
        UUID taskId,
        @NotBlank @jakarta.validation.constraints.Pattern(regexp = "COMPLETE|REOPEN") String decision,
        @Size(max = 500) String note) {}

    public record WorkShiftRequest(
        @NotBlank @Size(max = 40) String code,
        @NotBlank @Size(max = 120) String name,
        @NotBlank String startTime,
        @NotBlank String endTime,
        Integer breakMinutes,
        Integer lateGraceMinutes) {}



    public record PayrollRunDecisionRequest(
        UUID runId,
        @NotBlank @jakarta.validation.constraints.Pattern(regexp = "APPROVED|REJECTED|PAID") String decision) {}

    public record PayslipPublishRequest(
        UUID runId,
        @NotBlank @jakarta.validation.constraints.Pattern(regexp = "[A-Z]{3}") String currency) {}

    public record PerformanceReviewRequest(
        @NotNull UUID cycleId,
        @NotNull UUID employeeId,
        @NotNull UUID reviewerEmployeeId,
        @NotBlank @jakarta.validation.constraints.Pattern(regexp = "SELF|MANAGER|PEER|HR|360") String reviewType,
        @NotNull @jakarta.validation.constraints.DecimalMin("0") @jakarta.validation.constraints.DecimalMax("100") BigDecimal score,
        @Size(max = 4000) String strengths,
        @Size(max = 4000) String improvements,
        @Size(max = 4000) String developmentPlan) {}

    public record PerformanceReviewDecisionRequest(
        UUID reviewId,
        @NotBlank @jakarta.validation.constraints.Pattern(regexp = "SUBMITTED|COMPLETED|CANCELLED") String status) {}

    public record FeedbackRequest(
        @NotNull UUID employeeId,
        @NotBlank @Size(max = 4000) String feedback,
        @NotBlank @jakarta.validation.constraints.Pattern(regexp = "EMPLOYEE_ONLY|EMPLOYEE_AND_HR|MANAGEMENT") String visibility) {}

    public record CourseProgressRequest(
        UUID enrollmentId,
        @NotNull @jakarta.validation.constraints.DecimalMin("0") @jakarta.validation.constraints.DecimalMax("100") BigDecimal progressPercent,
        @jakarta.validation.constraints.DecimalMin("0") @jakarta.validation.constraints.DecimalMax("100") BigDecimal score) {}

    public record CertificateRequest(
        @NotNull UUID employeeId,
        @NotBlank @Size(max = 255) String name,
        String issuer,
        LocalDate issuedOn,
        LocalDate expiresOn,
        String credentialId,
        String documentObjectKey) {}

    public record SkillRequest(
        @NotNull UUID employeeId,
        @NotBlank @Size(max = 180) String skillName,
        @NotBlank @jakarta.validation.constraints.Pattern(regexp = "BEGINNER|INTERMEDIATE|ADVANCED|EXPERT") String proficiency,
        UUID verifiedByEmployeeId) {}

    public record OffboardingTaskDecisionRequest(
        UUID taskId,
        @NotBlank @jakarta.validation.constraints.Pattern(regexp = "COMPLETE|REOPEN") String decision,
        @Size(max = 500) String note) {}

    public record OffboardingDecisionRequest(
        UUID caseId,
        @NotBlank @jakarta.validation.constraints.Pattern(regexp = "IN_PROGRESS|COMPLETED|CANCELLED") String status) {}

    public record EmployeeDocumentRequest(
        @NotNull UUID employeeId,
        @NotBlank @Size(max = 60) String documentType,
        @NotBlank @Size(max = 255) String displayName,
        @NotBlank @Size(max = 500) String objectKey,
        @jakarta.validation.constraints.Pattern(regexp = "PUBLIC|INTERNAL|PRIVATE", message = "Invalid classification") String classification) {}

    public record ObjectiveProgressRequest(
        @NotNull @jakarta.validation.constraints.DecimalMin("0") BigDecimal currentValue) {}

    public record EmployeeUpdateRequest(
        @Size(max = 180) String fullName,
        @Email @Size(max = 254) String workEmail,
        UUID departmentId,
        @Size(max = 180) String jobTitle,
        UUID managerEmployeeId,
        @Size(max = 30) String employmentStatus) {}

    public record PrivateProfileUpdateRequest(
        LocalDate dateOfBirth,
        @Size(max = 40) String identityType,
        @Size(max = 255) String identityNumber,
        @Email @Size(max = 254) String personalEmail,
        @Size(max = 80) String personalPhone,
        @Size(max = 1000) String address,
        @Size(max = 180) String emergencyName,
        @Size(max = 80) String emergencyPhone) {}

    public record CreatedResource(UUID id, String status) {}
}
