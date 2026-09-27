export function Notice({ tone, children }: { tone: 'ok' | 'error'; children: string }) {
  return (
    <p
      role={tone === 'error' ? 'alert' : 'status'}
      className={[
        'rounded-xl px-3.5 py-2.5 text-sm font-semibold',
        tone === 'ok' ? 'bg-richtig-soft text-richtig' : 'bg-falsch-soft text-falsch',
      ].join(' ')}
    >
      {children}
    </p>
  );
}
