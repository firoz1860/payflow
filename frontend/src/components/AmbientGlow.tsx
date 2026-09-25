export function AmbientGlow() {
  return (
    <div className="pointer-events-none fixed inset-0 -z-10 overflow-hidden">
      <div className="absolute -left-24 -top-24 h-80 w-80 rounded-full bg-blue-200/35 blur-3xl" />
      <div className="absolute right-[-6rem] top-28 h-96 w-96 rounded-full bg-indigo-200/30 blur-3xl" />
      <div className="absolute bottom-[-8rem] left-1/3 h-96 w-96 rounded-full bg-cyan-100/35 blur-3xl" />
    </div>
  );
}
