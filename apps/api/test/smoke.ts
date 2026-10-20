import { config } from 'dotenv';
import { resolve } from 'node:path';
import { randomBytes, scryptSync } from 'node:crypto';
import { strict as assert } from 'node:assert';
import { PrismaClient } from '@erp/database';

config({ path: resolve(__dirname, '../../../.env') });
const db = new PrismaClient();
const base = `http://localhost:${process.env.API_PORT || 4000}/api/v1`;
const origin = process.env.APP_ORIGIN!;
const password = randomBytes(24).toString('base64url');
const salt = randomBytes(16).toString('hex');

async function request(path: string, options: RequestInit = {}, cookie?: string, companyId?: string) {
  return fetch(base + path, { ...options, headers: { ...(options.body ? { 'content-type': 'application/json' } : {}),
    ...(options.method && options.method !== 'GET' ? { origin } : {}), ...(cookie ? { cookie } : {}),
    ...(companyId ? { 'x-company-id': companyId } : {}), ...options.headers } });
}

async function main() {
  const company = await db.company.findUniqueOrThrow({ where: { code: process.env.BOOTSTRAP_COMPANY_CODE } });
  const role = await db.role.findUniqueOrThrow({ where: { companyId_name: { companyId: company.id, name: 'Warehouse Staff' } } });
  const email = `smoke-${Date.now()}@local.test`;
  let userId = '';
  let tempRoleId = '';
  let targetUserId = '';
  const businessResourceIds: string[] = [];
  let employeeId = '', departmentId = '', warehouseId = '', warehouse2Id = '', supplierId = '', payrollId = '', taskId = '', leaveId = '', kpiId = '', alertRuleId = '';
  let internalSkuId = '', purchaseRequestId = '', purchaseOrderId = '', paymentId = '', fulfillmentJobId = '', lifecycleStoreId = '', lifecycleOrderId = '', lifecycleProductId = '';
  try {
    const user = await db.user.create({ data: { email, name: 'Smoke Test', passwordHash: `${salt}:${scryptSync(password, salt, 64).toString('hex')}`,
      memberships: { create: { companyId: company.id } }, assignments: { create: { companyId: company.id, roleId: role.id } } } });
    userId = user.id;
    const login = await request('/auth/login', { method: 'POST', body: JSON.stringify({ email, password }) });
    assert.equal(login.status, 200);
    assert.deepEqual(await login.json(), { next: 'app' });
    const cookie = login.headers.get('set-cookie')?.split(';')[0];
    assert.ok(cookie?.startsWith('erp_session='));
    assert.equal((await request('/auth/me', {}, cookie)).status, 200);
    assert.equal((await request('/company', {}, cookie, company.id)).status, 200);
    assert.equal((await request('/stores', {}, cookie, company.id)).status, 200);
    assert.equal((await request('/users', {}, cookie, company.id)).status, 403);
    assert.equal((await request('/company', {}, cookie, '00000000-0000-0000-0000-000000000000')).status, 403);
    const limitedRole = await db.role.create({ data: { companyId: company.id, name: `Smoke users ${Date.now()}`,
      permissions: { create: ['users.read', 'users.write', 'sales.read', 'sales.write', 'inventory.read', 'inventory.write', 'inventory.approve', 'finance.read', 'finance.write', 'finance.approve', 'hr.read', 'hr.write', 'hr.approve', 'analytics.read', 'analytics.write', 'supplier.read', 'supplier.write', 'procurement.read', 'procurement.write', 'procurement.approve', 'pim.read', 'pim.write', 'fulfillment.read', 'fulfillment.write'].map(permissionCode => ({ permissionCode })) } } });
    tempRoleId = limitedRole.id;
    await db.userRoleAssignment.create({ data: { userId, companyId: company.id, roleId: tempRoleId } });
    assert.equal((await request('/users', {}, cookie, company.id)).status, 200);
    const adminRole = await db.role.findUniqueOrThrow({ where: { companyId_name: { companyId: company.id, name: 'Super Admin' } } });
    const escalate = await request('/users', { method: 'POST', body: JSON.stringify({ email: `escalate-${Date.now()}@local.test`, name: 'Escalation Test', password, roleId: adminRole.id }) }, cookie, company.id);
    assert.equal(escalate.status, 403);
    const allowedCreate = await request('/users', { method: 'POST', body: JSON.stringify({ email: `limited-${Date.now()}@local.test`, name: 'Limited Test', password, roleId: limitedRole.id }) }, cookie, company.id);
    assert.equal(allowedCreate.status, 201);
    targetUserId = (await allowedCreate.json() as { id: string }).id;
    const targetAssignment = await db.userRoleAssignment.findFirstOrThrow({ where: { userId: targetUserId, roleId: limitedRole.id } });
    assert.equal((await request(`/users/${targetUserId}/roles/${targetAssignment.id}`, { method: 'DELETE' }, cookie, company.id)).status, 200);
    assert.equal(await db.userRoleAssignment.count({ where: { userId: targetUserId } }), 0);

    const warehouseResponse = await request('/operations/warehouses', { method: 'POST', body: JSON.stringify({ code: `W${Date.now()}`, name: 'Smoke Warehouse' }) }, cookie, company.id);
    assert.equal(warehouseResponse.status, 201); warehouseId = (await warehouseResponse.json() as { id: string }).id; businessResourceIds.push(warehouseId);
    const supplierResponse = await request('/operations/suppliers', { method: 'POST', body: JSON.stringify({ code: `S${Date.now()}`, name: 'Smoke Supplier', email: 'supplier@local.test' }) }, cookie, company.id);
    assert.equal(supplierResponse.status, 201); supplierId = (await supplierResponse.json() as { id: string }).id; businessResourceIds.push(supplierId);
    const departmentResponse = await request('/company-management/departments', { method: 'POST', body: JSON.stringify({ code: `D${Date.now()}`, name: 'Smoke Department' }) }, cookie, company.id);
    assert.equal(departmentResponse.status, 201); departmentId = (await departmentResponse.json() as { id: string }).id; businessResourceIds.push(departmentId);
    const employeeResponse = await request('/company-management/employees', { method: 'POST', body: JSON.stringify({ employeeNumber: `E${Date.now()}`, fullName: 'Smoke Employee', jobTitle: 'QA', departmentId, employmentType: 'FULL_TIME', startDate: '2026-10-01', baseSalary: '1000', salaryCurrency: 'USD' }) }, cookie, company.id);
    assert.equal(employeeResponse.status, 201); employeeId = (await employeeResponse.json() as { id: string }).id; businessResourceIds.push(employeeId);
    const skuResponse = await request('/commerce/skus', { method: 'POST', body: JSON.stringify({ code: `LC${Date.now()}`, name: 'Lifecycle smoke SKU', barcode: `${Date.now()}` }) }, cookie, company.id);
    assert.equal(skuResponse.status, 201); internalSkuId = (await skuResponse.json() as { id: string }).id;
    const quoteResponse = await request('/lifecycle/suppliers/quotes', { method: 'POST', body: JSON.stringify({ supplierId, status: 'RECEIVED', currency: 'USD', leadTimeDays: 5, items: [{ internalSkuId, quantity: 4, minOrderQty: 1, unitPrice: '12.50' }] }) }, cookie, company.id);
    assert.equal(quoteResponse.status, 201);
    assert.equal((await request('/lifecycle/suppliers/contracts', { method: 'POST', body: JSON.stringify({ supplierId, title: 'Smoke supply agreement', status: 'ACTIVE', startDate: '2026-10-01', endDate: '2027-10-01', currency: 'USD', value: '1000' }) }, cookie, company.id)).status, 201);
    assert.equal((await request('/lifecycle/suppliers/reviews', { method: 'POST', body: JSON.stringify({ supplierId, periodStart: '2026-09-01', periodEnd: '2026-09-30', qualityScore: 5, deliveryScore: 4, priceScore: 4, serviceScore: 5 }) }, cookie, company.id)).status, 201);
    const prResponse = await request('/lifecycle/procurement/requests', { method: 'POST', body: JSON.stringify({ title: 'Lifecycle smoke purchase', departmentId, priority: 'HIGH', neededBy: '2026-11-01', items: [{ internalSkuId, quantity: 4, targetUnitCost: '12.50' }] }) }, cookie, company.id);
    assert.equal(prResponse.status, 201); purchaseRequestId = (await prResponse.json() as { id: string }).id;
    assert.equal((await request(`/lifecycle/procurement/requests/${purchaseRequestId}/submit`, { method: 'POST', body: '{}' }, cookie, company.id)).status, 201);
    assert.equal((await request(`/lifecycle/procurement/requests/${purchaseRequestId}/approve`, { method: 'POST', body: '{}' }, cookie, company.id)).status, 201);
    const convertResponse = await request(`/lifecycle/procurement/requests/${purchaseRequestId}/convert`, { method: 'POST', body: JSON.stringify({ supplierId, warehouseId, currency: 'USD' }) }, cookie, company.id);
    assert.equal(convertResponse.status, 201); purchaseOrderId = (await convertResponse.json() as { id: string }).id;
    const paymentResponse = await request('/lifecycle/procurement/payments', { method: 'POST', body: JSON.stringify({ purchaseOrderId, amount: '50.00', currency: 'USD', dueDate: '2026-11-15', paymentMethod: 'BANK_TRANSFER' }) }, cookie, company.id);
    assert.equal(paymentResponse.status, 201); paymentId = (await paymentResponse.json() as { id: string }).id;
    assert.equal((await request(`/lifecycle/procurement/payments/${paymentId}/submit`, { method: 'POST', body: '{}' }, cookie, company.id)).status, 201);
    assert.equal((await request(`/lifecycle/procurement/payments/${paymentId}/approve`, { method: 'POST', body: '{}' }, cookie, company.id)).status, 201);
    assert.equal((await request(`/lifecycle/procurement/payments/${paymentId}/paid`, { method: 'POST', body: JSON.stringify({ externalReference: 'SMOKE-PAYMENT' }) }, cookie, company.id)).status, 201);
    assert.equal((await request(`/lifecycle/pim/${internalSkuId}`, { method: 'PATCH', body: JSON.stringify({ lifecycleStatus: 'ACTIVE', brand: 'Smoke Brand', category: 'Test', description: 'Lifecycle integration test', specifications: { material: 'test' }, weightGrams: 250 }) }, cookie, company.id)).status, 200);
    assert.equal((await request(`/lifecycle/pim/${internalSkuId}/departments`, { method: 'POST', body: JSON.stringify({ departmentId, function: 'RND', ownerNote: 'Product owner' }) }, cookie, company.id)).status, 201);
    const store = await db.store.create({ data: { companyId: company.id, name: 'Lifecycle Store', shopifyDomain: `lifecycle-${Date.now()}.myshopify.com`, currency: 'USD' } }); lifecycleStoreId = store.id;
    const product = await db.product.create({ data: { storeId: store.id, shopifyId: `gid://shopify/Product/${Date.now()}`, title: 'Lifecycle product', status: 'ACTIVE', shopifyUpdatedAt: new Date() } }); lifecycleProductId = product.id;
    const variant = await db.variant.create({ data: { productId: product.id, shopifyId: `gid://shopify/ProductVariant/${Date.now()}`, title: 'Default', sku: 'SMOKE', price: '25', currency: 'USD', position: 1, mapping: { create: { internalSkuId } } } });
    const order = await db.order.create({ data: { storeId: store.id, shopifyId: `gid://shopify/Order/${Date.now()}`, name: `#SMOKE-${Date.now()}`, currency: 'USD', subtotal: '50', total: '50', totalTax: '0', totalShipping: '0', totalDiscount: '0', shopifyCreatedAt: new Date(), shopifyUpdatedAt: new Date(), items: { create: { shopifyId: `gid://shopify/LineItem/${Date.now()}`, variantId: variant.id, title: 'Lifecycle product', sku: 'SMOKE', quantity: 2, unitPrice: '25', discount: '0' } } } }); lifecycleOrderId = order.id;
    const orderItem = await db.orderItem.findFirstOrThrow({ where: { orderId: order.id } });
    await db.warehouseBalance.create({ data: { warehouseId, internalSkuId, quantity: 5 } });
    const fulfillmentResponse = await request('/lifecycle/fulfillment', { method: 'POST', body: JSON.stringify({ orderId: order.id, warehouseId, priority: 'HIGH', items: [{ orderItemId: orderItem.id, quantity: 2 }] }) }, cookie, company.id);
    assert.equal(fulfillmentResponse.status, 201); fulfillmentJobId = (await fulfillmentResponse.json() as { id: string }).id;
    assert.equal((await request(`/lifecycle/fulfillment/${fulfillmentJobId}/status`, { method: 'PATCH', body: JSON.stringify({ status: 'PICKING' }) }, cookie, company.id)).status, 200);
    assert.equal((await request(`/lifecycle/fulfillment/${fulfillmentJobId}/status`, { method: 'PATCH', body: JSON.stringify({ status: 'PACKED' }) }, cookie, company.id)).status, 200);
    assert.equal((await request(`/lifecycle/fulfillment/${fulfillmentJobId}/shipment`, { method: 'POST', body: JSON.stringify({ carrier: 'Smoke Carrier', trackingNumber: 'TRACK-SMOKE', currency: 'USD', shippingCost: '3.5' }) }, cookie, company.id)).status, 201);
    assert.equal((await request(`/lifecycle/fulfillment/${fulfillmentJobId}/status`, { method: 'PATCH', body: JSON.stringify({ status: 'SHIPPED' }) }, cookie, company.id)).status, 200);
    assert.equal((await db.warehouseBalance.findUniqueOrThrow({ where: { warehouseId_internalSkuId: { warehouseId, internalSkuId } } })).quantity, 3);
    const checkIn = await request('/company-management/attendances/check-in', { method: 'POST', body: JSON.stringify({ employeeId, status: 'REMOTE' }) }, cookie, company.id);
    assert.equal(checkIn.status, 201); const attendanceId = (await checkIn.json() as { id: string }).id; businessResourceIds.push(attendanceId);
    assert.equal((await request(`/company-management/attendances/${attendanceId}/check-out`, { method: 'POST', body: '{}' }, cookie, company.id)).status, 201);
    const leaveResponse = await request('/company-management/leave-requests', { method: 'POST', body: JSON.stringify({ employeeId, startDate: '2026-12-01', endDate: '2026-12-02', leaveType: 'ANNUAL', reason: 'Smoke test' }) }, cookie, company.id);
    assert.equal(leaveResponse.status, 201); leaveId = (await leaveResponse.json() as { id: string }).id; businessResourceIds.push(leaveId);
    assert.equal((await request(`/company-management/leave-requests/${leaveId}/submit`, { method: 'POST', body: '{}' }, cookie, company.id)).status, 201);
    assert.equal((await request(`/company-management/leave-requests/${leaveId}/approve`, { method: 'POST', body: '{}' }, cookie, company.id)).status, 201);
    const taskResponse = await request('/company-management/tasks', { method: 'POST', body: JSON.stringify({ title: 'Smoke task', priority: 'HIGH', assigneeIds: [employeeId] }) }, cookie, company.id);
    assert.equal(taskResponse.status, 201); taskId = (await taskResponse.json() as { id: string }).id; businessResourceIds.push(taskId);
    assert.equal((await request(`/company-management/tasks/${taskId}`, { method: 'PATCH', body: JSON.stringify({ status: 'DONE' }) }, cookie, company.id)).status, 200);
    const kpiResponse = await request('/company-management/kpis', { method: 'POST', body: JSON.stringify({ code: `K${Date.now()}`, name: 'Smoke KPI', unit: '%', period: 'MONTHLY', weight: 1, departmentId }) }, cookie, company.id);
    assert.equal(kpiResponse.status, 201); kpiId = (await kpiResponse.json() as { id: string }).id; businessResourceIds.push(kpiId);
    const payrollResponse = await request('/company-management/payroll-runs', { method: 'POST', body: JSON.stringify({ periodStart: '2026-10-01', periodEnd: '2026-10-31', currency: 'USD' }) }, cookie, company.id);
    assert.equal(payrollResponse.status, 201); payrollId = (await payrollResponse.json() as { id: string }).id; businessResourceIds.push(payrollId);
    assert.equal((await request(`/company-management/payroll-runs/${payrollId}/approve`, { method: 'POST', body: '{}' }, cookie, company.id)).status, 201);
    assert.equal((await request(`/company-management/payroll-runs/${payrollId}/export`, {}, cookie, company.id)).status, 200);
    const alertResponse = await request('/analytics/alerts/rules', { method: 'POST', body: JSON.stringify({ name: 'Smoke low stock', type: 'LOW_STOCK', threshold: '5', windowDays: 7, channels: ['IN_APP'] }) }, cookie, company.id);
    assert.equal(alertResponse.status, 201); alertRuleId = (await alertResponse.json() as { id: string }).id; businessResourceIds.push(alertRuleId);
    assert.equal((await request('/analytics/alerts/evaluate', { method: 'POST', body: '{}' }, cookie, company.id)).status, 201);
    assert.equal((await request('/analytics/daily-metrics/refresh', { method: 'POST', body: '{}' }, cookie, company.id)).status, 201);
    assert.equal((await request('/analytics/dashboard', {}, cookie, company.id)).status, 200);
    const warehouse2Response = await request('/operations/warehouses', { method: 'POST', body: JSON.stringify({ code: `W2${Date.now()}`, name: 'Hidden Warehouse' }) }, cookie, company.id);
    assert.equal(warehouse2Response.status, 201); warehouse2Id = (await warehouse2Response.json() as { id: string }).id; businessResourceIds.push(warehouse2Id);
    await db.userRoleAssignment.updateMany({ where: { userId, companyId: company.id }, data: { warehouseId } });
    const scopedWarehouses = await request('/operations/warehouses', {}, cookie, company.id);
    assert.equal(scopedWarehouses.status, 200);
    assert.deepEqual((await scopedWarehouses.json() as Array<{ id: string }>).map(row => row.id), [warehouseId]);
    assert.equal((await request('/operations/warehouses', { method: 'POST', body: JSON.stringify({ code: `W3${Date.now()}`, name: 'Forbidden Warehouse' }) }, cookie, company.id)).status, 403);
    const badOrigin = await request('/auth/logout', { method: 'POST', headers: { origin: 'https://attacker.example' } }, cookie);
    assert.equal(badOrigin.status, 403);
    const nextPassword = randomBytes(24).toString('base64url');
    assert.equal((await request('/auth/password', { method: 'POST', body: JSON.stringify({ currentPassword: password, newPassword: nextPassword }) }, cookie)).status, 201);
    assert.equal((await request('/auth/me', {}, cookie)).status, 401);
    const relogin = await request('/auth/login', { method: 'POST', body: JSON.stringify({ email, password: nextPassword }) });
    assert.equal(relogin.status, 200);
    const newCookie = relogin.headers.get('set-cookie')?.split(';')[0];
    assert.ok(newCookie);
    assert.equal((await request('/auth/logout', { method: 'POST' }, newCookie)).status, 201);

    const adminLogin = await request('/auth/login', { method: 'POST', body: JSON.stringify({ email: process.env.BOOTSTRAP_ADMIN_EMAIL, password: process.env.BOOTSTRAP_ADMIN_PASSWORD }) });
    assert.equal(adminLogin.status, 200);
    assert.deepEqual(await adminLogin.json(), { next: 'mfa-setup' });
    const adminCookie = adminLogin.headers.get('set-cookie')?.split(';')[0];
    assert.ok(adminCookie);
    const setup = await request('/auth/mfa/setup', { method: 'POST' }, adminCookie);
    assert.equal(setup.status, 201);
    const secret = (await setup.json() as { secret: string }).secret;
    assert.ok(secret.length >= 32);
    await request('/auth/logout', { method: 'POST' }, adminCookie);
    console.log('Integration smoke passed: auth/RBAC, supplier/procurement/PIM/fulfillment lifecycle, operations, people, finance and analytics.');
  } finally {
    if (fulfillmentJobId) await db.fulfillmentJob.deleteMany({ where: { id: fulfillmentJobId } });
    if (lifecycleOrderId) await db.order.deleteMany({ where: { id: lifecycleOrderId } });
    if (lifecycleProductId) await db.product.deleteMany({ where: { id: lifecycleProductId } });
    if (lifecycleStoreId) await db.store.deleteMany({ where: { id: lifecycleStoreId } });
    if (paymentId) await db.procurementPayment.deleteMany({ where: { id: paymentId } });
    if (purchaseOrderId) await db.purchaseOrder.deleteMany({ where: { id: purchaseOrderId } });
    if (purchaseRequestId) { await db.approvalRequest.deleteMany({ where: { entityType: 'PURCHASE_REQUEST', entityId: purchaseRequestId } }); await db.purchaseRequest.deleteMany({ where: { id: purchaseRequestId } }); }
    if (payrollId) await db.payrollRun.deleteMany({ where: { id: payrollId } });
    if (taskId) await db.workTask.deleteMany({ where: { id: taskId } });
    if (kpiId) await db.kpiDefinition.deleteMany({ where: { id: kpiId } });
    if (leaveId) await db.approvalRequest.deleteMany({ where: { entityType: 'LEAVE_REQUEST', entityId: leaveId } });
    if (employeeId) await db.employee.deleteMany({ where: { id: employeeId } });
    if (departmentId) await db.department.deleteMany({ where: { id: departmentId } });
    if (supplierId) {
      await db.supplierQuote.deleteMany({ where: { supplierId } });
      await db.supplierContract.deleteMany({ where: { supplierId } });
      await db.supplierPerformanceReview.deleteMany({ where: { supplierId } });
      await db.supplier.deleteMany({ where: { id: supplierId } });
    }
    if (internalSkuId) await db.internalSku.deleteMany({ where: { id: internalSkuId } });
    if (warehouse2Id) await db.warehouse.deleteMany({ where: { id: warehouse2Id } });
    if (warehouseId) await db.warehouse.deleteMany({ where: { id: warehouseId } });
    if (alertRuleId) await db.alertRule.deleteMany({ where: { id: alertRuleId } });
    if (businessResourceIds.length) await db.auditLog.deleteMany({ where: { companyId: company.id, resourceId: { in: businessResourceIds } } });
    if (targetUserId) {
      await db.auditLog.deleteMany({ where: { companyId: company.id, resourceId: targetUserId } });
      await db.user.delete({ where: { id: targetUserId } });
    }
    if (userId) { await db.auditLog.deleteMany({ where: { companyId: company.id, actorId: userId } }); await db.user.delete({ where: { id: userId } }); }
    if (tempRoleId) await db.role.delete({ where: { id: tempRoleId } });
    await db.$disconnect();
  }
}

main().catch(error => { console.error(error); process.exitCode = 1; });
