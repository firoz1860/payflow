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
import { ApiKeysPage } from './pages/ApiKeysPage';
import { MerchantProfilePage } from './pages/MerchantProfilePage';
import { QrCheckoutPage } from './pages/QrCheckoutPage';
import { AnalyticsPage } from './pages/AnalyticsPage';
import { LedgerPage } from './pages/LedgerPage';
import { LedgerPostingDetailPage } from './pages/LedgerPostingDetailPage';
import { WebhooksPage } from './pages/WebhooksPage';
import { MonitoringPage } from './pages/MonitoringPage';
import { DeveloperDocsPage } from './pages/DeveloperDocsPage';
import { SettingsPage } from './pages/SettingsPage';
import { AdminMerchantsPage } from './pages/AdminMerchantsPage';
import { AdminMerchantDetailPage } from './pages/AdminMerchantDetailPage';
import { AdminRolesPage } from './pages/AdminRolesPage';

export default function App() {
  return (
    <Routes>
      <Route path="/login" element={<LoginPage />} />
      <Route path="/register" element={<RegisterPage />} />
      <Route path="/forgot-password" element={<ForgotPasswordPage />} />
      <Route path="/reset-password/:token" element={<ResetPasswordPage />} />
      <Route path="/verify-email/:token" element={<VerifyEmailPage />} />

      <Route path="/dashboard" element={<ProtectedRoute><Layout><DashboardPage /></Layout></ProtectedRoute>} />
      <Route path="/payments" element={<ProtectedRoute permission="payments:read"><Layout><PaymentsPage /></Layout></ProtectedRoute>} />
      <Route path="/payments/create" element={<ProtectedRoute permission="payments:create"><Layout><CreatePaymentPage /></Layout></ProtectedRoute>} />
      <Route path="/payments/:reference" element={<ProtectedRoute permission="payments:read"><Layout><PaymentDetailPage /></Layout></ProtectedRoute>} />
      <Route path="/api-keys" element={<ProtectedRoute permission="api_keys:manage"><Layout><ApiKeysPage /></Layout></ProtectedRoute>} />
      <Route path="/payments/:reference/checkout" element={<ProtectedRoute permission="payments:read"><Layout><QrCheckoutPage /></Layout></ProtectedRoute>} />
      <Route path="/analytics" element={<ProtectedRoute permission="payments:read"><Layout><AnalyticsPage /></Layout></ProtectedRoute>} />
      <Route path="/ledger" element={<ProtectedRoute permission="ledger:read"><Layout><LedgerPage /></Layout></ProtectedRoute>} />
      <Route path="/ledger/:postingId" element={<ProtectedRoute permission="ledger:read"><Layout><LedgerPostingDetailPage /></Layout></ProtectedRoute>} />
      <Route path="/webhooks" element={<ProtectedRoute permission="webhooks:manage"><Layout><WebhooksPage /></Layout></ProtectedRoute>} />
      <Route path="/monitoring" element={<ProtectedRoute><Layout><MonitoringPage /></Layout></ProtectedRoute>} />
      <Route path="/developers" element={<ProtectedRoute><Layout><DeveloperDocsPage /></Layout></ProtectedRoute>} />
      <Route path="/settings" element={<ProtectedRoute permission="merchant:read"><Layout><SettingsPage /></Layout></ProtectedRoute>} />
      <Route path="/merchant" element={<ProtectedRoute permission="merchant:read"><Layout><MerchantProfilePage /></Layout></ProtectedRoute>} />
      <Route path="/admin/merchants" element={<ProtectedRoute permission="platform:admin"><Layout><AdminMerchantsPage /></Layout></ProtectedRoute>} />
      <Route path="/admin/merchants/:id" element={<ProtectedRoute permission="platform:admin"><Layout><AdminMerchantDetailPage /></Layout></ProtectedRoute>} />
      <Route path="/admin/roles" element={<ProtectedRoute permission="platform:admin"><Layout><AdminRolesPage /></Layout></ProtectedRoute>} />

      <Route path="/" element={<Navigate to="/dashboard" replace />} />
      <Route path="*" element={<Navigate to="/dashboard" replace />} />
    </Routes>
  );
}
