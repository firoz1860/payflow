import { FormEvent, useEffect, useState } from 'react';
import { Link } from 'react-router-dom';
import { PageHeader } from '../components/PageHeader';
import { CardSpinner } from '../components/Spinner';
import { extractError } from '../api';
import { getHeroContent, saveHeroContent, type HeroContent } from '../services/heroContentService';

const fields = [
  { key: 'badge', label: 'Badge', max: 100 },
  { key: 'title', label: 'Headline', max: 100 },
  { key: 'accent', label: 'Highlighted headline', max: 140 },
  { key: 'description', label: 'Description', max: 600 },
  { key: 'supportingText', label: 'Supporting text', max: 300 },
  { key: 'primaryLabel', label: 'Primary button label', max: 60 },
  { key: 'secondaryLabel', label: 'Secondary button label', max: 60 },
] as const;

export function HeroContentPage() {
  const [content, setContent] = useState<HeroContent | null>(null);
  const [loading, setLoading] = useState(true);
  const [saving, setSaving] = useState(false);
  const [error, setError] = useState('');
  const [saved, setSaved] = useState(false);

  async function load() {
    setLoading(true); setError(''); setSaved(false);
    try { setContent(await getHeroContent()); }
    catch (e) { setError(extractError(e)); }
    finally { setLoading(false); }
  }
  useEffect(() => { void load(); }, []);

  async function save(event: FormEvent) {
    event.preventDefault();
    if (!content) return;
    setSaving(true); setError(''); setSaved(false);
    try { setContent(await saveHeroContent(content)); setSaved(true); }
    catch (e) { setError(extractError(e)); }
    finally { setSaving(false); }
  }

  return <>
    <PageHeader title="Website content" description="Edit the landing page hero. Saving publishes these changes for every visitor." />
    {error && <div role="alert" className="mb-5 rounded-xl border border-red-200 bg-red-50 p-4 text-red-700">
      <p>{error}</p><button type="button" className="mt-2 underline" disabled={loading || saving} onClick={() => void load()}>Reload saved content</button>
    </div>}
    {saved && <p role="status" className="mb-5 rounded-xl bg-emerald-50 p-4 text-emerald-800">Published. Visitors will see the new content when they open the landing page.</p>}
    {loading ? <CardSpinner /> : content && <div className="grid gap-6 lg:grid-cols-2">
      <form onSubmit={save} className="card space-y-5 p-6">
        {fields.map(field => <div key={field.key}>
          <label className="label" htmlFor={`hero-${field.key}`}>{field.label}</label>
          {field.key === 'description' || field.key === 'supportingText'
            ? <textarea id={`hero-${field.key}`} required maxLength={field.max} className="input min-h-24" disabled={saving}
                value={content[field.key]} onChange={e => { setSaved(false); setContent({ ...content, [field.key]: e.target.value }); }} />
            : <input id={`hero-${field.key}`} required maxLength={field.max} className="input" disabled={saving}
                value={content[field.key]} onChange={e => { setSaved(false); setContent({ ...content, [field.key]: e.target.value }); }} />}
        </div>)}
        <fieldset className="space-y-3"><legend className="label">Trust points</legend>
          {content.trustPoints.map((point, index) => <div key={index}>
            <label htmlFor={`trust-${index}`} className="sr-only">Trust point {index + 1}</label>
            <input id={`trust-${index}`} required maxLength={80} className="input" disabled={saving} value={point}
              onChange={e => { setSaved(false); setContent({ ...content, trustPoints: content.trustPoints.map((p, i) => i === index ? e.target.value : p) }); }} />
          </div>)}
        </fieldset>
        <button className="btn-primary" disabled={saving} type="submit">{saving ? 'Publishing…' : 'Save and publish'}</button>
      </form>
      <section aria-label="Hero preview" className="card h-fit break-words p-6 sm:p-8">
        <p className="mb-6 text-xs font-semibold uppercase tracking-wider text-slate-500">Live preview</p>
        <p className="text-sm font-semibold text-brand-700">{content.badge}</p>
        <h2 className="mt-4 text-3xl font-bold sm:text-4xl">{content.title} <span className="text-brand-600">{content.accent}</span></h2>
        <p className="mt-5 text-slate-600">{content.description}</p>
        <p className="mt-3 text-sm text-slate-500">{content.supportingText}</p>
        <div className="mt-6 flex flex-wrap gap-3"><span className="btn-primary">{content.primaryLabel}</span><span className="btn-secondary">{content.secondaryLabel}</span></div>
        <ul className="mt-6 space-y-2 text-sm text-slate-500">{content.trustPoints.map((p, i) => <li key={i}>{p}</li>)}</ul>
        <Link className="mt-6 inline-block text-sm text-brand-600 underline" to="/website">Open landing page</Link>
      </section>
    </div>}
  </>;
}
