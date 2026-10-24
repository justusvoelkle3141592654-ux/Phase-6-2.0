import { useState, type FormEvent } from 'react';
import { KeyRound, Pencil, Plus, Trash2 } from 'lucide-react';
import { PASSWORD_MIN_LENGTH, type AdminUserDto } from '@wordflow/shared';
import { useI18n } from '../../i18n';
import { useCreateUser, useAdminUsers, useDeleteUser, useUpdateUser } from '../../lib/admin';
import { errorMessage } from '../../lib/errors';
import { fill } from '../../lib/format';
import { Notice } from '../Notice';

interface Fields {
  email: string;
  name: string;
  password: string;
}

/** E-mail, name and password inputs; the password is optional when editing. */
function UserForm({
  idPrefix,
  initial,
  passwordOptional,
  submitLabel,
  pending,
  error,
  onSubmit,
  onCancel,
}: {
  idPrefix: string;
  initial: Fields;
  passwordOptional?: boolean;
  submitLabel: string;
  pending: boolean;
  error: unknown;
  onSubmit: (fields: Fields) => void;
  onCancel: () => void;
}) {
  const { m } = useI18n();
  const [fields, setFields] = useState(initial);
  const set = (key: keyof Fields) => (e: { target: { value: string } }) =>
    setFields((f) => ({ ...f, [key]: e.target.value }));
  const passwordOk = fields.password.length >= PASSWORD_MIN_LENGTH;
  const valid =
    fields.email.trim() !== '' && (passwordOk || (passwordOptional && fields.password === ''));

  const submit = (event: FormEvent) => {
    event.preventDefault();
    if (valid) onSubmit(fields);
  };

  return (
    <form className="space-y-3 rounded-2xl bg-surface-2 p-4" onSubmit={submit}>
      <div className="grid gap-3 sm:grid-cols-2">
        <div>
          <label className="label" htmlFor={`${idPrefix}-email`}>
            {m.auth.email}
          </label>
          <input
            id={`${idPrefix}-email`}
            className="field bg-surface"
            type="email"
            autoComplete="off"
            value={fields.email}
            onChange={set('email')}
          />
        </div>
        <div>
          <label className="label" htmlFor={`${idPrefix}-name`}>
            {m.account.name}
          </label>
          <input
            id={`${idPrefix}-name`}
            className="field bg-surface"
            maxLength={80}
            value={fields.name}
            onChange={set('name')}
          />
        </div>
      </div>
      <div>
        <label className="label" htmlFor={`${idPrefix}-password`}>
          {passwordOptional ? m.admin.newPassword : m.auth.password}
        </label>
        <input
          id={`${idPrefix}-password`}
          className="field bg-surface"
          type="password"
          autoComplete="new-password"
          value={fields.password}
          onChange={set('password')}
          aria-describedby={`${idPrefix}-password-hint`}
        />
        <p id={`${idPrefix}-password-hint`} className="hint">
          {passwordOptional ? m.admin.passwordKeep : m.auth.passwordHint}
        </p>
      </div>
      {error != null && <Notice tone="error">{errorMessage(m, error)}</Notice>}
      <div className="flex flex-wrap gap-2">
        <button type="submit" className="btn btn-primary" disabled={!valid || pending}>
          {submitLabel}
        </button>
        <button type="button" className="btn btn-ghost" onClick={onCancel}>
          {m.packages.cancel}
        </button>
      </div>
    </form>
  );
}

function UserRow({ user }: { user: AdminUserDto }) {
  const { m } = useI18n();
  const [editing, setEditing] = useState<'edit' | 'password' | null>(null);
  const update = useUpdateUser();
  const remove = useDeleteUser();
  const admin = user.role === 'admin';

  if (editing) {
    return (
      <li className="py-3">
        <UserForm
          idPrefix={`user-${user.id}`}
          initial={{ email: user.email, name: user.name, password: '' }}
          passwordOptional
          submitLabel={m.account.save}
          pending={update.isPending}
          error={update.error}
          onCancel={() => {
            update.reset();
            setEditing(null);
          }}
          onSubmit={(f) =>
            update.mutate(
              {
                id: user.id,
                email: f.email,
                name: f.name,
                ...(f.password ? { password: f.password } : {}),
              },
              { onSuccess: () => setEditing(null) },
            )
          }
        />
      </li>
    );
  }

  return (
    <li className="flex flex-wrap items-center gap-x-4 gap-y-2 py-3">
      <div className="min-w-0 flex-1 basis-48">
        <p className="font-semibold break-words">
          {user.name || user.email}
          {admin && (
            <span className="ml-2 rounded-full bg-primary px-2 py-0.5 text-xs font-semibold text-on-primary">
              {m.admin.superuser}
            </span>
          )}
        </p>
        {user.name && <p className="text-sm break-all text-ink-soft">{user.email}</p>}
      </div>
      <div className="flex items-center gap-1">
        <button
          type="button"
          className="btn btn-ghost min-h-9 px-2.5"
          aria-label={`${m.packages.edit}: ${user.email}`}
          onClick={() => setEditing('edit')}
        >
          <Pencil className="size-4" aria-hidden="true" />
        </button>
        <button
          type="button"
          className="btn btn-ghost min-h-9 px-2.5"
          aria-label={`${m.admin.setPassword}: ${user.email}`}
          title={m.admin.setPassword}
          onClick={() => setEditing('password')}
        >
          <KeyRound className="size-4" aria-hidden="true" />
        </button>
        {!admin && (
          <button
            type="button"
            className="btn btn-ghost min-h-9 px-2.5"
            aria-label={`${m.packages.delete}: ${user.email}`}
            disabled={remove.isPending}
            onClick={() => {
              if (window.confirm(fill(m.admin.deleteConfirm, { email: user.email })))
                remove.mutate(user.id);
            }}
          >
            <Trash2 className="size-4" aria-hidden="true" />
          </button>
        )}
      </div>
    </li>
  );
}

/** Superuser: list, add, change and delete accounts. */
export function AccountsAdmin() {
  const { m } = useI18n();
  const users = useAdminUsers();
  const create = useCreateUser();
  const [adding, setAdding] = useState(false);

  return (
    <section className="panel" aria-labelledby="accounts-title">
      <div className="mb-2 flex flex-wrap items-center justify-between gap-3">
        <h2 id="accounts-title" className="text-xl font-bold">
          {m.admin.accountsTitle}
        </h2>
        {!adding && (
          <button type="button" className="btn btn-secondary" onClick={() => setAdding(true)}>
            <Plus className="size-4.5" aria-hidden="true" />
            {m.admin.addAccount}
          </button>
        )}
      </div>
      <p className="mb-4 text-sm text-ink-soft">{m.admin.accountsIntro}</p>
      {adding && (
        <div className="mb-4">
          <UserForm
            idPrefix="new-user"
            initial={{ email: '', name: '', password: '' }}
            submitLabel={m.admin.addAccount}
            pending={create.isPending}
            error={create.error}
            onCancel={() => {
              create.reset();
              setAdding(false);
            }}
            onSubmit={(f) => create.mutate(f, { onSuccess: () => setAdding(false) })}
          />
        </div>
      )}
      {users.isPending ? (
        <p className="text-ink-soft">{m.common.loading}</p>
      ) : users.isError ? (
        <Notice tone="error">{errorMessage(m, users.error)}</Notice>
      ) : (
        <ul className="divide-y divide-rule">
          {users.data.map((u) => (
            <UserRow key={u.id} user={u} />
          ))}
        </ul>
      )}
    </section>
  );
}
