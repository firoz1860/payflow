import { ReactNode } from 'react';
import { Sidebar } from './Sidebar';
import { ToastContainer } from './Toast';
import { PageTransition } from '../lib/motion';
import { useLocation } from 'react-router-dom';

export function Layout({ children }: { children: ReactNode }) {
  const location = useLocation();
  return (
    <div className="flex min-h-screen bg-slate-50">
      <Sidebar />
      <main className="flex-1 overflow-x-hidden">
        <div className="max-w-7xl mx-auto px-4 sm:px-6 py-8 pt-16 lg:pt-8">
          <PageTransition key={location.pathname}>
            {children}
          </PageTransition>
        </div>
      </main>
      <ToastContainer />
    </div>
  );
}
