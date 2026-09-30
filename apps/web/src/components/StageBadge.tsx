import type { VocabDto } from '@wordflow/shared';
import { useI18n } from '../i18n';
import { fill } from '../lib/format';

/** Small grey bar that fills up with the stage. */
export function StageMeter({ stage }: { stage: number }) {
  return (
    <span className="flex gap-0.5" aria-hidden="true">
      {[1, 2, 3, 4, 5, 6].map((n) => (
        <span
          key={n}
          className={[
            'h-2.5 w-1.5 rounded-full',
            n <= stage ? 'bg-primary' : 'bg-rule-strong/60',
          ].join(' ')}
        />
      ))}
    </span>
  );
}

export function StageBadge({ vocab }: { vocab: Pick<VocabDto, 'active' | 'stage' | 'learned'> }) {
  const { m } = useI18n();
  if (!vocab.active) {
    return (
      <span className="rounded-full bg-surface-2 px-2.5 py-0.5 text-xs font-semibold text-ink-soft">
        {m.packages.inactive}
      </span>
    );
  }
  if (vocab.learned) {
    return (
      <span className="rounded-full bg-primary px-2.5 py-0.5 text-xs font-semibold text-on-primary">
        {m.packages.learned}
      </span>
    );
  }
  return (
    <span className="inline-flex items-center gap-2 text-xs font-semibold text-ink-soft">
      <StageMeter stage={vocab.stage} />
      {fill(m.packages.stage, { n: vocab.stage })}
    </span>
  );
}
