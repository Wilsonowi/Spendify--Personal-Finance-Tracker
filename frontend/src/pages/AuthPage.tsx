import { useState, type FormEvent } from 'react'
import { LockKeyhole, ScanLine, ShieldCheck, WalletCards } from 'lucide-react'
import { api, initializeCsrf } from '../api'
import { queryClient } from '../query'
import type { User } from '../types'

export function AuthPage() {
  const [mode, setMode] = useState<'login' | 'register'>('login')
  const [error, setError] = useState('')
  const [pending, setPending] = useState(false)

  async function submit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    setError('')
    setPending(true)
    const data = new FormData(event.currentTarget)
    const payload = {
      email: data.get('email'),
      password: data.get('password'),
      ...(mode === 'register' ? { displayName: data.get('displayName') } : {}),
    }
    try {
      await initializeCsrf()
      const user = await api<User>(`/auth/${mode}`, { method: 'POST', body: JSON.stringify(payload) })
      queryClient.setQueryData(['user'], user)
      await initializeCsrf()
    } catch (reason) {
      setError(reason instanceof Error ? reason.message : 'Authentication failed.')
    } finally {
      setPending(false)
    }
  }

  return (
    <main className="auth-page">
      <section className="auth-context">
        <div className="wordmark"><span className="brand-mark">S</span><span>Spendify</span></div>
        <div className="auth-copy">
          <p className="eyebrow">Private finance workspace</p>
          <h1>Upload the evidence.<br />Keep the numbers honest.</h1>
          <p>Turn Public Bank and TNG transaction slips into a reviewable ledger without counting wallet top-ups twice.</p>
        </div>
        <div className="trust-list">
          <span><ShieldCheck /> Encrypted originals</span>
          <span><ScanLine /> Self-hosted OCR</span>
          <span><WalletCards /> Transfer reconciliation</span>
        </div>
      </section>
      <section className="auth-form-wrap">
        <form onSubmit={submit} className="auth-form">
          <LockKeyhole size={28} />
          <div><p className="eyebrow">{mode === 'login' ? 'Welcome back' : 'Local account'}</p><h2>{mode === 'login' ? 'Open your ledger' : 'Create your workspace'}</h2></div>
          {mode === 'register' ? <label>Display name<input name="displayName" minLength={2} maxLength={80} required autoComplete="name" /></label> : null}
          <label>Email<input name="email" type="email" required autoComplete="email" /></label>
          <label>Password<input name="password" type="password" minLength={mode === 'register' ? 12 : 1} required autoComplete={mode === 'login' ? 'current-password' : 'new-password'} /></label>
          {error ? <p className="form-error" role="alert">{error}</p> : null}
          <button className="primary-button wide" disabled={pending}>{pending ? 'Please wait...' : mode === 'login' ? 'Sign in' : 'Create account'}</button>
          <button type="button" className="text-button" onClick={() => setMode(mode === 'login' ? 'register' : 'login')}>
            {mode === 'login' ? 'Create a local account' : 'I already have an account'}
          </button>
        </form>
      </section>
    </main>
  )
}
