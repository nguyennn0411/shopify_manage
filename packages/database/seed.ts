import { config as loadEnv } from 'dotenv';
import { resolve } from 'node:path';
import { PrismaClient } from '@prisma/client';
import { randomBytes, scryptSync } from 'node:crypto';

loadEnv({ path: resolve(__dirname, '../../.env') });
const db = new PrismaClient();
const permissionDescriptions: Record<string, string> = {
  'company.read': 'Xem công ty',
  'company.write': 'Sửa cấu hình công ty',
  'users.read': 'Xem người dùng',
  'users.write': 'Quản trị người dùng',
  'roles.read': 'Xem vai trò',
  'roles.write': 'Quản trị vai trò',
  'stores.read': 'Xem cửa hàng',
  'stores.write': 'Quản trị cấu hình cửa hàng',
  'audit.read': 'Xem nhật ký kiểm toán',
  'sales.read': 'Xem bán hàng',
  'sales.write': 'Xử lý bán hàng',
  'inventory.read': 'Xem tồn kho',
  'inventory.write': 'Xử lý tồn kho',
  'inventory.approve': 'Duyệt tồn kho',
  'finance.read': 'Xem tài chính',
  'finance.write': 'Ghi nhận tài chính',
  'finance.approve': 'Duyệt tài chính',
  'hr.read': 'Xem nhân sự',
  'hr.write': 'Quản trị nhân sự',
  'hr.approve': 'Duyệt nhân sự',
  'analytics.read': 'Xem BI và cảnh báo',
  'analytics.write': 'Quản trị BI, tích hợp và tự động hóa',
  'supplier.read': 'Xem nhà cung cấp, báo giá và hợp đồng',
  'supplier.write': 'Quản trị nhà cung cấp, báo giá và hợp đồng',
  'procurement.read': 'Xem yêu cầu mua, PO, thanh toán và nhập hàng',
  'procurement.write': 'Tạo và xử lý mua hàng',
  'procurement.approve': 'Duyệt yêu cầu mua hàng',
  'pim.read': 'Xem hồ sơ thông tin sản phẩm',
  'pim.write': 'Quản trị PIM và đồng bộ Shopify',
  'fulfillment.read': 'Xem xử lý đơn và vận chuyển',
  'fulfillment.write': 'Xử lý picking, đóng gói, giao và tracking',
};

const rolePermissions: Record<string, string[]> = {
  'Super Admin': Object.keys(permissionDescriptions),
  CEO: ['company.read', 'users.read', 'roles.read', 'stores.read', 'audit.read', 'sales.read', 'inventory.read', 'finance.read', 'finance.approve', 'hr.read', 'analytics.read', 'supplier.read', 'procurement.read', 'procurement.approve', 'pim.read', 'fulfillment.read'],
  'Operations Manager': ['company.read', 'stores.read', 'sales.read', 'sales.write', 'inventory.read', 'inventory.write', 'inventory.approve', 'finance.read', 'analytics.read', 'analytics.write', 'supplier.read', 'supplier.write', 'procurement.read', 'procurement.write', 'procurement.approve', 'pim.read', 'pim.write', 'fulfillment.read', 'fulfillment.write'],
  Accountant: ['company.read', 'stores.read', 'sales.read', 'inventory.read', 'finance.read', 'finance.write', 'supplier.read', 'procurement.read'],
  'Warehouse Staff': ['company.read', 'stores.read', 'sales.read', 'inventory.read', 'inventory.write', 'procurement.read', 'fulfillment.read', 'fulfillment.write'],
  Marketing: ['company.read', 'stores.read', 'sales.read', 'finance.read', 'analytics.read', 'analytics.write', 'pim.read'],
  Sales: ['company.read', 'stores.read', 'sales.read', 'sales.write', 'inventory.read', 'pim.read', 'fulfillment.read'],
  'R&D': ['company.read', 'inventory.read', 'supplier.read', 'pim.read', 'pim.write'],
  'HR Manager': ['company.read', 'hr.read', 'hr.write', 'hr.approve', 'finance.read'],
};

async function main() {
  const name = process.env.BOOTSTRAP_COMPANY_NAME?.trim();
  const code = process.env.BOOTSTRAP_COMPANY_CODE?.trim().toUpperCase();
  const email = process.env.BOOTSTRAP_ADMIN_EMAIL?.trim().toLowerCase();
  const password = process.env.BOOTSTRAP_ADMIN_PASSWORD;
  if (!name || !code || !email || !password || password.length < 12) {
    throw new Error('Set BOOTSTRAP_COMPANY_NAME, BOOTSTRAP_COMPANY_CODE, BOOTSTRAP_ADMIN_EMAIL and a password of at least 12 characters');
  }
  const company = await db.company.upsert({ where: { code }, update: {}, create: { name, code } });
  for (const [permissionCode, description] of Object.entries(permissionDescriptions)) {
    await db.permission.upsert({ where: { code: permissionCode }, update: { description }, create: { code: permissionCode, description } });
  }
  for (const [roleName, codes] of Object.entries(rolePermissions)) {
    const role = await db.role.upsert({
      where: { companyId_name: { companyId: company.id, name: roleName } },
      update: {},
      create: { companyId: company.id, name: roleName, isSystem: true },
    });
    for (const permissionCode of codes) {
      await db.rolePermission.upsert({
        where: { roleId_permissionCode: { roleId: role.id, permissionCode } },
        update: {},
        create: { roleId: role.id, permissionCode },
      });
    }
  }
  for (const category of [
    { code: 'SHIPPING', name: 'Shipping & logistics' }, { code: 'MARKETING', name: 'Marketing & advertising' },
    { code: 'SOFTWARE', name: 'Software & subscriptions' }, { code: 'PAYROLL', name: 'Payroll' }, { code: 'OTHER', name: 'Other' },
  ]) {
    await db.expenseCategory.upsert({ where: { companyId_code: { companyId: company.id, code: category.code } }, update: { name: category.name }, create: { companyId: company.id, ...category } });
  }
  let user = await db.user.findUnique({ where: { email } });
  if (!user) {
    const salt = randomBytes(16).toString('hex');
    const hash = scryptSync(password, salt, 64).toString('hex');
    user = await db.user.create({ data: { email, name: 'Administrator', passwordHash: `${salt}:${hash}` } });
  }
  await db.companyMembership.upsert({
    where: { companyId_userId: { companyId: company.id, userId: user.id } },
    update: {},
    create: { companyId: company.id, userId: user.id },
  });
  const adminRole = await db.role.findUniqueOrThrow({ where: { companyId_name: { companyId: company.id, name: 'Super Admin' } } });
  const assignment = await db.userRoleAssignment.findFirst({ where: { companyId: company.id, userId: user.id, roleId: adminRole.id, storeId: null } });
  if (!assignment) await db.userRoleAssignment.create({ data: { companyId: company.id, userId: user.id, roleId: adminRole.id } });
  console.log(`Foundation seed ready for ${company.code} (${email}). Existing passwords are never overwritten.`);
}

main().finally(() => db.$disconnect());
