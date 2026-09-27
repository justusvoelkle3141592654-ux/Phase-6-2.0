import { EmptyState, PageHeader } from '../components/Page';
import { useI18n } from '../i18n';

export function LearnPage() {
  const { m } = useI18n();
  return (
    <>
      <PageHeader title={m.learn.title} />
      <EmptyState>{m.learn.empty}</EmptyState>
    </>
  );
}
