import { ReactNode } from 'react';
import { Sidebar } from './Sidebar';
import { ToastContainer } from './Toast';

export function Layout({ children }: { children: ReactNode }) {
  return (
    <div className="flex min-h-screen bg-slate-50">
      <Sidebar />
      <main className="flex-1 overflow-x-auto">
        <div className="max-w-7xl mx-auto px-6 py-8 animate-fade-in">
          {children}
        </div>
      </main>
      <ToastContainer />
    </div>
  );
}
