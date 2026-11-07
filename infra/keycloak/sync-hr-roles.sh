#!/usr/bin/env bash
set -euo pipefail

: "${KEYCLOAK_INTERNAL_URL:?KEYCLOAK_INTERNAL_URL is required}"
: "${KEYCLOAK_ADMIN_USER:?KEYCLOAK_ADMIN_USER is required}"
: "${KEYCLOAK_ADMIN_PASSWORD:?KEYCLOAK_ADMIN_PASSWORD is required}"
: "${KEYCLOAK_REALM:?KEYCLOAK_REALM is required}"

KCADM=/opt/keycloak/bin/kcadm.sh
"$KCADM" config credentials --server "$KEYCLOAK_INTERNAL_URL" --realm master \
  --user "$KEYCLOAK_ADMIN_USER" --password "$KEYCLOAK_ADMIN_PASSWORD" >/dev/null

roles=(
  hr.access
  hr.employee.read.basic hr.employee.read.sensitive hr.employee.write
  hr.recruitment.read hr.recruitment.write
  hr.onboarding.read hr.onboarding.write
  hr.attendance.read hr.attendance.write hr.attendance.approve
  hr.leave.read hr.leave.write hr.leave.approve
  hr.payroll.read.own hr.payroll.read.all hr.payroll.write
  hr.performance.read hr.performance.write
  hr.training.read hr.training.write
  hr.offboarding.read hr.offboarding.write
  pm.access
  pm.project.read pm.project.write
  pm.task.read pm.task.write
  pm.sprint.read pm.sprint.write pm.workflow.manage
  pm.timesheet.read pm.timesheet.read.own pm.timesheet.write pm.timesheet.approve
  pm.workload.read pm.report.read
  pm.issue.read pm.issue.write
)

for role in "${roles[@]}"; do
  if ! "$KCADM" get "roles/$role" -r "$KEYCLOAK_REALM" >/dev/null 2>&1; then
    "$KCADM" create roles -r "$KEYCLOAK_REALM" -s "name=$role" >/dev/null
    echo "Created realm role: $role"
  fi
done

"$KCADM" config unset >/dev/null 2>&1 || true
echo "HR realm roles are synchronized."
