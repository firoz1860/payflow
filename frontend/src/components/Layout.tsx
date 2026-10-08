import { ReactNode } from 'react';
import { useLocation } from 'react-router-dom';
import { TopNav } from './dashboard/TopNav';
import { AmbientGlow } from './AmbientGlow';
import { ToastContainer } from './Toast';
import { PageTransition } from '../lib/motion';

export function Layout({ children }: { children: ReactNode }) {
  const location = useLocation();

  return (
    <div className="relative min-h-screen overflow-x-hidden bg-slate-50/60 text-slate-800">
      <AmbientGlow />
      <TopNav />

      <main className="px-4 pb-16 pt-6 sm:px-6 lg:px-8">
        <div className="mx-auto max-w-7xl">
          <PageTransition key={location.pathname}>{children}</PageTransition>
        </div>
      </main>

      <ToastContainer />
    </div>
  );
}
