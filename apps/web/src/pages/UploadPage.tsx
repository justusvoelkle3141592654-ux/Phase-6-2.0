import { EmptyState, PageHeader } from '../components/Page';
import { useI18n } from '../i18n';

export function UploadPage() {
  const { m } = useI18n();
  return (
    <>
      <PageHeader title={m.upload.title} />
      <EmptyState>{m.upload.empty}</EmptyState>
    </>
  );
}
