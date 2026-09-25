import { Navigate, Route, Routes } from 'react-router-dom';
import { ProtectedRoute } from './components/ProtectedRoute';
import { Layout } from './components/Layout';
import { LoginPage } from './pages/LoginPage';
import { RegisterPage } from './pages/RegisterPage';
import { ForgotPasswordPage } from './pages/ForgotPasswordPage';
import { ResetPasswordPage } from './pages/ResetPasswordPage';
import { VerifyEmailPage } from './pages/VerifyEmailPage';
import { DashboardPage } from './pages/DashboardPage';
import { PaymentsPage } from './pages/PaymentsPage';
import { PaymentDetailPage } from './pages/PaymentDetailPage';
import { CreatePaymentPage } from './pages/CreatePaymentPage';
import { QrPaymentsPage } from './pages/QrPaymentsPage';
import { ApiKeysPage } from './pages/ApiKeysPage';
import { MerchantProfilePage } from './pages/MerchantProfilePage';
import { LedgerPage } from './pages/LedgerPage';
import { AnalyticsPage } from './pages/AnalyticsPage';
import { MonitoringPage } from './pages/MonitoringPage';
import { WebhooksPage } from './pages/WebhooksPage';
import { DevelopersPage } from './pages/DevelopersPage';
import { AdminMerchantsPage } from './pages/AdminMerchantsPage';
import { AdminMerchantDetailPage } from './pages/AdminMerchantDetailPage';
import { AdminRolesPage } from './pages/AdminRolesPage';

const protectedPage = (page: React.ReactNode, permission?: string) => (
  <ProtectedRoute permission={permission}>
    <Layout>{page}</Layout>
  </ProtectedRoute>
);

export default function App() {
  return (
    <Routes>
      <Route path="/login" element={<LoginPage />} />
      <Route path="/register" element={<RegisterPage />} />
      <Route path="/forgot-password" element={<ForgotPasswordPage />} />
      <Route path="/reset-password/:token" element={<ResetPasswordPage />} />
      <Route path="/verify-email/:token" element={<VerifyEmailPage />} />

      <Route path="/dashboard" element={protectedPage(<DashboardPage />)} />
      <Route path="/payments" element={protectedPage(<PaymentsPage />, 'payments:read')} />
      <Route path="/payments/create" element={protectedPage(<CreatePaymentPage />, 'payments:create')} />
      <Route path="/payments/qr" element={protectedPage(<QrPaymentsPage />, 'payments:create')} />
      <Route path="/payments/:reference" element={protectedPage(<PaymentDetailPage />, 'payments:read')} />
      <Route path="/transactions" element={<Navigate to="/payments" replace />} />

      <Route path="/ledger" element={protectedPage(<LedgerPage />)} />
      <Route path="/analytics" element={protectedPage(<AnalyticsPage />)} />
      <Route path="/webhooks" element={protectedPage(<WebhooksPage />)} />
      <Route path="/developers" element={protectedPage(<DevelopersPage />)} />

      <Route path="/api-keys" element={protectedPage(<ApiKeysPage />, 'api_keys:manage')} />
      <Route path="/merchant" element={protectedPage(<MerchantProfilePage />, 'merchant:read')} />
      <Route path="/settings" element={protectedPage(<MerchantProfilePage />, 'merchant:read')} />

      <Route path="/monitoring" element={protectedPage(<MonitoringPage />, 'platform:admin')} />
      <Route path="/admin/merchants" element={protectedPage(<AdminMerchantsPage />, 'platform:admin')} />
      <Route path="/admin/merchants/:id" element={protectedPage(<AdminMerchantDetailPage />, 'platform:admin')} />
      <Route path="/admin/roles" element={protectedPage(<AdminRolesPage />, 'platform:admin')} />

      <Route path="/" element={<Navigate to="/dashboard" replace />} />
      <Route path="*" element={<Navigate to="/dashboard" replace />} />
    </Routes>
  );
}
