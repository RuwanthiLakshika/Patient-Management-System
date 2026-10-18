import { TOKEN_KEY } from '../constants'

export async function request(path, options = {}) {
  const token = localStorage.getItem(TOKEN_KEY)
  const response = await fetch(path, {
    ...options,
    headers: {
      'Content-Type': 'application/json',
      ...(token ? { Authorization: `Bearer ${token}` } : {}),
      ...(options.headers || {}),
    },
  })

  if (!response.ok) {
    let message = `Request failed (${response.status})`
    try {
      const body = await response.json()
      message = body.message || body.error || message
    } catch {
      // Some backend errors intentionally have no response body.
    }
    throw new Error(message)
  }

  return response.status === 204 ? null : response.json()
}

export const authApi = {
  login: (credentials) => request('/auth/login', {
    method: 'POST',
    body: JSON.stringify(credentials),
  }),
}

export const patientApi = {
  list: () => request('/api/patients'),
  create: (patient) => request('/api/patients', {
    method: 'POST',
    body: JSON.stringify(patient),
  }),
  update: (patient) => request(`/api/patients/${patient.id}`, {
    method: 'PUT',
    body: JSON.stringify(patient),
  }),
  remove: (id) => request(`/api/patients/${id}`, { method: 'DELETE' }),
}

export const serviceApi = {
  billingAccounts: () => request('/api/billing/accounts'),
  analyticsOverview: () => request('/api/analytics/overview'),
}
