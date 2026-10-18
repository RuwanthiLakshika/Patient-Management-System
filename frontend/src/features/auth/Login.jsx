import { useState } from 'react'
import { authApi } from '../../api/client'
import { TOKEN_KEY } from '../../constants'
import Icon from '../../components/Icon'

export default function Login({ onLogin }) {
  const [form, setForm] = useState({ email: 'testuser@test.com', password: '' })
  const [error, setError] = useState('')
  const [busy, setBusy] = useState(false)

  async function submit(event) {
    event.preventDefault()
    setError('')
    setBusy(true)
    try {
      const result = await authApi.login(form)
      localStorage.setItem(TOKEN_KEY, result.token)
      onLogin()
    } catch (err) {
      setError(err.message === 'Request failed (401)'
        ? 'The email or password is incorrect.'
        : err.message)
    } finally {
      setBusy(false)
    }
  }

  return (
    <main className="auth-shell">
      <section className="auth-brand">
        <div className="brand-mark"><Icon name="activity" /></div>
        <p className="eyebrow">Careflow health platform</p>
        <h1>Make every patient interaction count.</h1>
        <p className="auth-copy">A calm, connected workspace for care teams to manage patient records with confidence.</p>
        <div className="trust-row"><span>●</span> Secure access <span>●</span> Real-time records</div>
      </section>
      <section className="login-card">
        <div className="logo-line"><div className="brand-mark small"><Icon name="activity" /></div><strong>Careflow</strong></div>
        <h2>Welcome back</h2>
        <p className="muted">Sign in to your clinical workspace.</p>
        <form onSubmit={submit}>
          <label>Email address<input type="email" required value={form.email} onChange={(e) => setForm({ ...form, email: e.target.value })} /></label>
          <label>Password<input type="password" required minLength="8" placeholder="Enter your password" value={form.password} onChange={(e) => setForm({ ...form, password: e.target.value })} /></label>
          {error && <div className="alert error">{error}</div>}
          <button className="primary full" disabled={busy}>{busy ? 'Signing in…' : 'Sign in'} <span>→</span></button>
        </form>
        <p className="demo-hint">Use the seeded account from <code>auth-service/src/main/resources/data.sql</code>.</p>
      </section>
    </main>
  )
}
