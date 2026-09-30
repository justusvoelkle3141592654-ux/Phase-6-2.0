import { useI18n } from '../i18n';
import { PageHeader, EmptyState } from '../components/Page';
import { Link } from 'react-router';
import { Camera } from 'lucide-react';

// Placeholder page for Latin declension classes. In a full implementation,
// this would provide a UI to select and practice declension tables.
export function DeclensionsPage() {
  const { m } = useI18n();
  return (
    <>
      <PageHeader title={m.declensions.title} />
      <EmptyState
        action={
          <Link to="/learn" className="btn btn-primary">
            <Camera className="size-5" aria-hidden="true" />
            {m.learn.start}
          </Link>
        }
      >
        {/* Placeholder text – replace with actual declension UI later. */}
        <p>Hier können Sie die Deklinationsklassen des Lateins lernen.</p>
      </EmptyState>
    </>
  );
}
