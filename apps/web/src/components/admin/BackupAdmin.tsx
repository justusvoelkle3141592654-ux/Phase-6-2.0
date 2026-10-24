import { useRef } from 'react';
import { Download, Upload } from 'lucide-react';
import { useI18n } from '../../i18n';
import { useDownloadBackup, useRestoreBackup } from '../../lib/admin';
import { errorMessage } from '../../lib/errors';
import { Notice } from '../Notice';

/** Superuser: export the whole database as ZIP and import it again. */
export function BackupAdmin() {
  const { m } = useI18n();
  const fileRef = useRef<HTMLInputElement>(null);
  const download = useDownloadBackup();
  const restore = useRestoreBackup();

  return (
    <section className="panel space-y-4" aria-labelledby="backup-title">
      <h2 id="backup-title" className="text-xl font-bold">
        {m.admin.backupTitle}
      </h2>
      <p className="text-ink-soft">{m.admin.backupIntro}</p>
      <div className="flex flex-wrap gap-2">
        <button
          type="button"
          className="btn btn-primary"
          onClick={() => download.mutate()}
          disabled={download.isPending}
        >
          <Download className="size-4.5" aria-hidden="true" />
          {download.isPending ? m.admin.exporting : m.admin.export}
        </button>
        <button
          type="button"
          className="btn btn-secondary"
          onClick={() => fileRef.current?.click()}
          disabled={restore.isPending}
        >
          <Upload className="size-4.5" aria-hidden="true" />
          {restore.isPending ? m.admin.importing : m.admin.import}
        </button>
      </div>
      <input
        ref={fileRef}
        type="file"
        accept=".zip,application/zip"
        className="hidden"
        data-testid="backup-input"
        onChange={(e) => {
          const file = e.target.files?.[0];
          e.target.value = '';
          if (file && window.confirm(m.admin.importConfirm)) restore.mutate(file);
        }}
      />
      <p className="hint">{m.admin.importHint}</p>
      {download.isError && <Notice tone="error">{errorMessage(m, download.error)}</Notice>}
      {restore.isError && <Notice tone="error">{errorMessage(m, restore.error)}</Notice>}
    </section>
  );
}
