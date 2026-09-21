import { Navigate, Route, Routes } from 'react-router-dom';
import { ProtectedRoute } from './components/ProtectedRoute';
import { Layout } from './components/Layout';
import { LoginPage } from './pages/LoginPage';
import { RegisterPage } from './pages/RegisterPage';
import { DashboardPage } from './pages/DashboardPage';
import { PaymentsPage } from './pages/PaymentsPage';
import { PaymentDetailPage } from './pages/PaymentDetailPage';
import { CreatePaymentPage } from './pages/CreatePaymentPage';
import { ApiKeysPage } from './pages/ApiKeysPage';
import { MerchantProfilePage } from './pages/MerchantProfilePage';
import { AdminMerchantsPage } from './pages/AdminMerchantsPage';

export default function App() {
  return (
    <Routes>
      <Route path="/login" element={<LoginPage />} />
      <Route path="/register" element={<RegisterPage />} />

      <Route path="/dashboard" element={<ProtectedRoute><Layout><DashboardPage /></Layout></ProtectedRoute>} />
      <Route path="/payments" element={<ProtectedRoute permission="payments:read"><Layout><PaymentsPage /></Layout></ProtectedRoute>} />
      <Route path="/payments/create" element={<ProtectedRoute permission="payments:create"><Layout><CreatePaymentPage /></Layout></ProtectedRoute>} />
      <Route path="/payments/:reference" element={<ProtectedRoute permission="payments:read"><Layout><PaymentDetailPage /></Layout></ProtectedRoute>} />
      <Route path="/api-keys" element={<ProtectedRoute permission="api_keys:manage"><Layout><ApiKeysPage /></Layout></ProtectedRoute>} />
      <Route path="/merchant" element={<ProtectedRoute permission="merchant:read"><Layout><MerchantProfilePage /></Layout></ProtectedRoute>} />
      <Route path="/admin/merchants" element={<ProtectedRoute permission="platform:admin"><Layout><AdminMerchantsPage /></Layout></ProtectedRoute>} />

      <Route path="/" element={<Navigate to="/dashboard" replace />} />
      <Route path="*" element={<Navigate to="/dashboard" replace />} />
    </Routes>
  );
}
