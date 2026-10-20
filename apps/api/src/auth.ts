import { Body, Controller, Get, HttpCode, HttpException, HttpStatus, Post, Req, Res, BadRequestException, ForbiddenException, Injectable, UnauthorizedException } from '@nestjs/common';
import { IsEmail, IsString, Length } from 'class-validator';
import type { Request, Response } from 'express';
import { DatabaseService, Public, RedisService, SessionService, AppRequest, SessionData } from './core';
import { decryptSecret, encryptSecret, hashPassword, newTotpSecret, verifyPassword, verifyTotp } from './security';

class LoginDto {
  @IsEmail() email!: string;
  @IsString() password!: string;
}
class CodeDto { @IsString() @Length(6, 6) code!: string; }
class ChangePasswordDto {
  @IsString() currentPassword!: string;
  @IsString() @Length(12, 128) newPassword!: string;
}

@Injectable()
export class AuthService {
  constructor(private readonly db: DatabaseService, private readonly redis: RedisService, private readonly sessions: SessionService) {}

  async login(dto: LoginDto, request: Request, response: Response) {
    const email = dto.email.trim().toLowerCase();
    const ip = request.ip || 'unknown';
    const key = `login:${ip}:${email}`;
    const attempts = await this.redis.incr(key);
    if (attempts === 1) await this.redis.expire(key, 900);
    if (attempts > 8) throw new HttpException('Thử lại sau 15 phút', HttpStatus.TOO_MANY_REQUESTS);
    const user = await this.db.user.findUnique({ where: { email }, include: { assignments: { include: { role: { include: { permissions: true } } } } } });
    if (!user || user.status !== 'ACTIVE' || !verifyPassword(dto.password, user.passwordHash)) {
      throw new UnauthorizedException('Email hoặc mật khẩu không đúng');
    }
    await this.redis.del(key);
    const requiresMfa = user.assignments.some(a => a.role.name === 'Super Admin' || a.role.permissions.some(p => p.permissionCode.startsWith('finance.')));
    const state: SessionData['state'] = user.mfaEnabled ? 'mfa-required' : requiresMfa ? 'mfa-enroll' : 'active';
    await this.sessions.destroy(request.cookies?.erp_session);
    const token = await this.sessions.create({ userId: user.id, version: user.sessionVersion, state, createdAt: Date.now() });
    this.setCookie(response, token);
    if (state === 'active') await this.db.user.update({ where: { id: user.id }, data: { lastLoginAt: new Date() } });
    return { next: state === 'mfa-enroll' ? 'mfa-setup' : state === 'mfa-required' ? 'mfa-verify' : 'app' };
  }

  async setup(request: Request) {
    const data = await this.pending(request, ['mfa-enroll', 'active']);
    const user = await this.db.user.findUniqueOrThrow({ where: { id: data.userId } });
    if (user.mfaEnabled) throw new BadRequestException('MFA đã bật');
    const secret = newTotpSecret();
    await this.redis.set(`mfa-setup:${data.userId}`, encryptSecret(secret), 'EX', 600);
    return { secret, otpauthUrl: `otpauth://totp/${encodeURIComponent('Company ERP:' + user.email)}?secret=${secret}&issuer=Company%20ERP&algorithm=SHA1&digits=6&period=30` };
  }

  async confirm(request: Request, response: Response, code: string) {
    const data = await this.pending(request, ['mfa-enroll', 'active']);
    await this.mfaAttempt(data.userId);
    const encrypted = await this.redis.get(`mfa-setup:${data.userId}`);
    if (!encrypted || !verifyTotp(decryptSecret(encrypted), code)) throw new BadRequestException('Mã MFA không hợp lệ');
    await this.db.user.update({ where: { id: data.userId }, data: { mfaSecret: encrypted, mfaEnabled: true, lastLoginAt: new Date() } });
    await this.redis.del(`mfa-setup:${data.userId}`);
    await this.redis.del(`mfa-attempt:${data.userId}`);
    await this.sessions.update(request.cookies.erp_session, { ...data, state: 'active' });
    return { next: 'app' };
  }

  async verify(request: Request, code: string) {
    const data = await this.pending(request, ['mfa-required']);
    await this.mfaAttempt(data.userId);
    const user = await this.db.user.findUniqueOrThrow({ where: { id: data.userId } });
    if (!user.mfaSecret || !verifyTotp(decryptSecret(user.mfaSecret), code)) throw new UnauthorizedException('Mã MFA không hợp lệ');
    await this.redis.del(`mfa-attempt:${data.userId}`);
    await this.sessions.update(request.cookies.erp_session, { ...data, state: 'active' });
    await this.db.user.update({ where: { id: data.userId }, data: { lastLoginAt: new Date() } });
    return { next: 'app' };
  }

  async logout(request: Request, response: Response) {
    await this.sessions.destroy(request.cookies?.erp_session);
    response.clearCookie('erp_session', { path: '/' });
    return { ok: true };
  }

  async me(request: AppRequest) {
    const user = await this.db.user.findUniqueOrThrow({ where: { id: request.session!.userId }, select: {
      id: true, email: true, name: true, mfaEnabled: true,
      memberships: { select: { company: { select: { id: true, name: true, code: true, baseCurrency: true, timezone: true } } } },
      assignments: { select: { companyId: true, storeId: true, warehouseId: true, role: { select: { id: true, name: true, permissions: { select: { permissionCode: true } } } } } },
    } });
    return user;
  }

  async changePassword(request: AppRequest, dto: ChangePasswordDto) {
    const user = await this.db.user.findUniqueOrThrow({ where: { id: request.session!.userId } });
    if (!verifyPassword(dto.currentPassword, user.passwordHash)) throw new ForbiddenException('Mật khẩu hiện tại không đúng');
    await this.db.user.update({ where: { id: user.id }, data: { passwordHash: hashPassword(dto.newPassword), sessionVersion: { increment: 1 } } });
    await this.sessions.destroy(request.cookies.erp_session);
    return { relogin: true };
  }

  private async pending(request: Request, allowed: SessionData['state'][]) {
    const data = await this.sessions.read(request.cookies?.erp_session);
    if (!data || !allowed.includes(data.state)) throw new UnauthorizedException();
    const user = await this.db.user.findUnique({ where: { id: data.userId }, select: { status: true, sessionVersion: true } });
    if (!user || user.status !== 'ACTIVE' || user.sessionVersion !== data.version) throw new UnauthorizedException();
    return data;
  }
  private async mfaAttempt(userId: string) {
    const key = `mfa-attempt:${userId}`;
    const count = await this.redis.incr(key);
    if (count === 1) await this.redis.expire(key, 900);
    if (count > 8) throw new HttpException('Thử lại sau 15 phút', HttpStatus.TOO_MANY_REQUESTS);
  }
  private setCookie(response: Response, token: string) {
    response.cookie('erp_session', token, { httpOnly: true, sameSite: 'lax', secure: process.env.SESSION_COOKIE_SECURE === 'true', path: '/', maxAge: 12 * 60 * 60 * 1000 });
  }
}

@Controller('auth')
export class AuthController {
  constructor(private readonly auth: AuthService) {}
  @Public() @Post('login') @HttpCode(200) login(@Body() dto: LoginDto, @Req() req: Request, @Res({ passthrough: true }) res: Response) { return this.auth.login(dto, req, res); }
  @Public() @Post('mfa/setup') setup(@Req() req: Request) { return this.auth.setup(req); }
  @Public() @Post('mfa/confirm') confirm(@Req() req: Request, @Res({ passthrough: true }) res: Response, @Body() dto: CodeDto) { return this.auth.confirm(req, res, dto.code); }
  @Public() @Post('mfa/verify') verify(@Req() req: Request, @Body() dto: CodeDto) { return this.auth.verify(req, dto.code); }
  @Public() @Post('logout') logout(@Req() req: Request, @Res({ passthrough: true }) res: Response) { return this.auth.logout(req, res); }
  @Get('me') me(@Req() req: AppRequest) { return this.auth.me(req); }
  @Post('password') password(@Req() req: AppRequest, @Body() dto: ChangePasswordDto) { return this.auth.changePassword(req, dto); }
}
