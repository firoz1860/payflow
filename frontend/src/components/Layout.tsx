import { ReactNode } from 'react';
import { Sidebar } from './Sidebar';
import { ToastContainer } from './Toast';
import { PageTransition } from '../lib/motion';
import { useLocation } from 'react-router-dom';

export function Layout({ children }: { children: ReactNode }) {
  const location = useLocation();

  return (
    <div className="relative flex min-h-screen overflow-hidden bg-slate-50">
      <div aria-hidden className="pointer-events-none fixed inset-0 -z-10">
        <div className="absolute -top-32 left-[18%] h-80 w-80 rounded-full bg-blue-300/30 blur-3xl" />
        <div className="absolute top-[35%] right-[8%] h-96 w-96 rounded-full bg-indigo-300/20 blur-3xl" />
        <div className="absolute bottom-[-8rem] left-[42%] h-96 w-96 rounded-full bg-cyan-200/20 blur-3xl" />
      </div>

      <Sidebar />

      <main className="relative flex-1 overflow-x-hidden">
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
