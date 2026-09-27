import { EmptyState, PageHeader } from '../components/Page';
import { useI18n } from '../i18n';

export function PackagesPage() {
  const { m } = useI18n();
  return (
    <>
      <PageHeader title={m.packages.title} />
      <EmptyState>{m.packages.empty}</EmptyState>
    </>
  );
}
