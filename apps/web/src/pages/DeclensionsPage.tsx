import { useState } from 'react';
import { useI18n } from '../i18n';
import { PageHeader, EmptyState } from '../components/Page';
import { Link } from 'react-router';
import { Camera } from 'lucide-react';

// Placeholder page for Latin declension classes. In a full implementation,
// this would provide a UI to select and practice declension tables.
export function DeclensionsPage() {
  const { m } = useI18n();
  const [selected, setSelected] = useState('');
  const declensions = [
    {
      name: 'puella',
      rows: [
        { case: 'Nominativ', singular: 'puella', plural: 'puellae' },
        { case: 'Genitiv', singular: 'puellae', plural: 'puellārum' },
        { case: 'Akkusativ', singular: 'puellam', plural: 'puellās' },
        { case: 'Dativ', singular: 'puellae', plural: 'puellīs' },
        { case: 'Ablativ', singular: 'puellā', plural: 'puellīs' },
      ],
    },
    {
      name: 'servus',
      rows: [
        { case: 'Nominativ', singular: 'servus', plural: 'servī' },
        { case: 'Genitiv', singular: 'servī', plural: 'servōrum' },
        { case: 'Akkusativ', singular: 'servum', plural: 'servōs' },
        { case: 'Dativ', singular: 'servō', plural: 'servīs' },
        { case: 'Ablativ', singular: 'servō', plural: 'servīs' },
      ],
    },
    {
      name: 'rex',
      rows: [
        { case: 'Nominativ', singular: 'rex', plural: 'rēgēs' },
        { case: 'Genitiv', singular: 'rēgis', plural: 'rēgum' },
        { case: 'Akkusativ', singular: 'rēgem', plural: 'rēgēs' },
        { case: 'Dativ', singular: 'rēgī', plural: 'rēgibus' },
        { case: 'Ablativ', singular: 'rēge', plural: 'rēgibus' },
      ],
    },
  ];
  const decl = declensions.find((d) => d.name === selected);

  return (
    <>
      <PageHeader title={m.declensions.title} />
      <div className="mb-4">
        <label className="label" htmlFor="decl-select">
          {m.declensions.select}
        </label>
        <select
          id="decl-select"
          className="field"
          value={selected}
          onChange={(e) => setSelected(e.target.value)}
        >
          <option value="">—</option>
          {declensions.map((d) => (
            <option key={d.name} value={d.name}>
              {d.name}
            </option>
          ))}
        </select>
      </div>
      {decl && (
        <table className="table-auto w-full border border-gray-200 mb-4">
          <thead>
            <tr>
              <th className="px-2 py-1 border">Kasus</th>
              <th className="px-2 py-1 border">Singular</th>
              <th className="px-2 py-1 border">Plural</th>
            </tr>
          </thead>
          <tbody>
            {decl.rows.map((row) => (
              <tr key={row.case}>
                <td className="px-2 py-1 border">{row.case}</td>
                <td className="px-2 py-1 border">{row.singular}</td>
                <td className="px-2 py-1 border">{row.plural}</td>
              </tr>
            ))}
          </tbody>
        </table>
      )}
      <EmptyState
        action={
          <Link to="/learn" className="btn btn-primary">
            <Camera className="size-5" aria-hidden="true" />
            {m.learn.start}
          </Link>
        }
      >
        {/* Placeholder text – replace with actual declension UI later. */}
        <p>Wähle ein Substantiv, um die Deklinationstabelle zu sehen.</p>
      </EmptyState>
    </>
  );
}
