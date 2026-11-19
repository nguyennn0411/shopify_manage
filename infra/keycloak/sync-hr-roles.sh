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
  identity.account.read identity.account.write
  identity.role.read identity.role.write
  hr.access
  hr.employee.read.basic hr.employee.read.sensitive hr.employee.write hr.employee.write.sensitive
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
  pm.workload.read pm.workload.write pm.report.read
  pm.issue.read pm.issue.write
  crm.access
  crm.customer.read crm.customer.write
  crm.pipeline.read crm.pipeline.write
  crm.quote.read crm.quote.write crm.quote.approve
  crm.contract.read crm.contract.write
  crm.order.read crm.order.write
  crm.support.read crm.support.write
  crm.marketing.read crm.marketing.write
  finance.access
  finance.transaction.read finance.transaction.write finance.transaction.approve
  finance.budget.read finance.budget.write
  finance.invoice.read finance.invoice.write
  finance.advance.read finance.advance.write finance.advance.approve
  finance.project-cost.read finance.project-cost.write
  finance.accounting.read finance.accounting.write finance.period.close
  finance.report.read
  operations.access
  supplier.read supplier.write
  procurement.read procurement.write procurement.approve
  inventory.read inventory.write inventory.count.approve
  asset.read asset.write
  maintenance.read maintenance.write
  license.read license.write
  manufacturing.read manufacturing.write
  fulfillment.read fulfillment.write
  workplace.access
  document.read document.write document.share
  knowledge.read knowledge.write
  approval.read approval.write approval.decide
  form.read form.write form.submit
  signature.read signature.write signature.sign
  helpdesk.read helpdesk.write helpdesk.manage
  communication.read communication.write communication.moderate
  legal.read legal.write
  platform.search
  notification.read notification.write notification.manage
  saved-view.read saved-view.write saved-view.share
  automation.access automation.read automation.write automation.run
  integration.read integration.write
  webhook.read webhook.write
  reporting.access reporting.read reporting.manage reporting.export
  strategy.read strategy.write
)

for role in "${roles[@]}"; do
  if ! "$KCADM" get "roles/$role" -r "$KEYCLOAK_REALM" >/dev/null 2>&1; then
    "$KCADM" create roles -r "$KEYCLOAK_REALM" -s "name=$role" >/dev/null
    echo "Created realm role: $role"
  fi
done

"$KCADM" config unset >/dev/null 2>&1 || true
echo "ERP realm roles are synchronized."
