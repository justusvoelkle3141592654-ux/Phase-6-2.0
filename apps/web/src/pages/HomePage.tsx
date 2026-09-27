import { EmptyState, PageHeader } from '../components/Page';
import { useI18n } from '../i18n';

export function HomePage() {
  const { m } = useI18n();
  return (
    <>
      <PageHeader title={m.home.title} />
      <EmptyState>{m.home.empty}</EmptyState>
    </>
  );
}
