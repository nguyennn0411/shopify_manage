import { ArrowRight, Building2, LockKeyhole, ShieldCheck } from 'lucide-react';
import { Navigate, useSearchParams } from 'react-router-dom';
import { useAuth } from './AuthProvider';

export function LoginPage() {
  const { user, loading, login } = useAuth();
  const [params] = useSearchParams();
  if (!loading && user) return <Navigate to="/" replace />;
  return <main className="login-shell"><section className="login-visual"><div className="brand light"><span className="brand-mark"><Building2 /></span><strong>Company ERP</strong></div><div><span className="eyebrow"><ShieldCheck size={16} /> Zero Trust workspace</span><h1>Vận hành doanh nghiệp<br />trên một nền tảng an toàn.</h1><p>Danh tính được quản lý tập trung bằng Keycloak, phiên nằm trong Redis và mọi quyền dữ liệu được kiểm tra tại Spring Boot.</p></div><small><LockKeyhole size={15} /> OAuth2/OIDC · MFA · RBAC · Audit</small></section><section className="login-form"><div><span className="login-icon"><ShieldCheck /></span><p className="kicker">Company ERP</p><h2>Đăng nhập quản trị</h2><p>Sử dụng tài khoản doanh nghiệp và MFA do Keycloak quản lý.</p>{params.get('reason') === 'session-expired' && <div className="notice error">Phiên đã hết hạn. Vui lòng đăng nhập lại.</div>}<button className="primary login-button" disabled={loading} onClick={login}>{loading ? 'Đang kiểm tra phiên…' : 'Tiếp tục với Keycloak'} <ArrowRight size={17} /></button><small>Bạn sẽ được chuyển tới cổng định danh tập trung. ERP không nhận hoặc lưu mật khẩu của bạn.</small></div></section></main>;
}
