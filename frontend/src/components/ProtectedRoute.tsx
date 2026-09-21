import { Navigate, useLocation } from 'react-router-dom';
import { useAuthStore } from '../store/auth';
import { FullPageSpinner } from './Spinner';
import { useEffect, useState } from 'react';
import { getMe } from '../services/authService';

interface ProtectedRouteProps {
  children: React.ReactNode;
  permission?: string;
}

export function ProtectedRoute({ children, permission }: ProtectedRouteProps) {
  const { isAuthenticated, user, hasPermission, setUser, accessToken } = useAuthStore();
  const location = useLocation();
  const [loading, setLoading] = useState(!user && isAuthenticated);

  useEffect(() => {
    if (isAuthenticated && accessToken && !user) {
      getMe()
        .then(setUser)
        .catch(() => useAuthStore.getState().logout())
        .finally(() => setLoading(false));
    } else {
      setLoading(false);
    }
  }, [isAuthenticated, accessToken, user, setUser]);

  if (!isAuthenticated) {
    return <Navigate to="/login" state={{ from: location }} replace />;
  }

  if (loading) return <FullPageSpinner />;

  if (permission && !hasPermission(permission)) {
    return <Navigate to="/dashboard" replace />;
  }

  return <>{children}</>;
}
