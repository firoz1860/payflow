import { ReactNode } from 'react';
import { useLocation } from 'react-router-dom';
import { Sidebar } from './Sidebar';
import { Topbar } from './Topbar';
import { AmbientGlow } from './AmbientGlow';
import { ToastContainer } from './Toast';
import { PageTransition } from '../lib/motion';

export function Layout({ children }: { children: ReactNode }) {
  const location = useLocation();

  return (
    <div className="relative min-h-screen overflow-x-hidden bg-slate-50/60 text-slate-800">
      <AmbientGlow />
      <Sidebar />
      <Topbar />

      <main className="min-h-screen px-4 pb-12 pt-24 sm:px-6 lg:pl-72 lg:pr-6">
        <div className="mx-auto max-w-7xl">
          <PageTransition key={location.pathname}>{children}</PageTransition>
        </div>
      </main>

      <ToastContainer />
    </div>
  );
}
