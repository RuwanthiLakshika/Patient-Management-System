import { useState } from 'react'
import { TOKEN_KEY } from './constants'
import Dashboard from './features/dashboard/Dashboard'
import Login from './features/auth/Login'

export default function App() {
  const [authenticated, setAuthenticated] = useState(
    Boolean(localStorage.getItem(TOKEN_KEY)),
  )

  function logout() {
    localStorage.removeItem(TOKEN_KEY)
    setAuthenticated(false)
  }

  return authenticated
    ? <Dashboard onLogout={logout} />
    : <Login onLogin={() => setAuthenticated(true)} />
}
