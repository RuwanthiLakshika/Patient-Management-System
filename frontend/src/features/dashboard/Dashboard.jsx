import { useEffect, useMemo, useState } from 'react'
import Icon from '../../components/Icon'
import PatientModal from '../patients/PatientModal'
import { patientApi, serviceApi } from '../../api/client'

function ServicePanels({ billingAccounts, analytics }) {
  return (
    <section className="service-section">
      <div className="section-heading"><div><p className="eyebrow">Microservice observability</p><h2>Connected services</h2></div><span className="live-pill"><i /> Live through API Gateway</span></div>
      <div className="service-grid">
        <article className="service-panel billing-panel"><div className="service-title"><div className="service-icon"><Icon name="activity" /></div><div><h3>Billing service</h3><span>gRPC account provisioning</span></div><b className="service-status">● Online</b></div><div className="service-metric"><strong>{billingAccounts.length}</strong><span>billing accounts created</span></div><div className="service-list">{billingAccounts.slice(-3).reverse().map((account) => <div className="service-list-row" key={account.accountId}><span className="mini-avatar">{account.patientName?.charAt(0)}</span><div><strong>{account.patientName}</strong><small>{account.accountId} · {account.status}</small></div><em>Active</em></div>)}{billingAccounts.length === 0 && <p className="service-empty">Create a patient to trigger account creation through gRPC.</p>}</div></article>
        <article className="service-panel analytics-panel"><div className="service-title"><div className="service-icon"><Icon name="activity" /></div><div><h3>Analytics service</h3><span>Kafka event consumer</span></div><b className="service-status">● {analytics.status === 'CONSUMING_KAFKA' ? 'Online' : 'Connecting'}</b></div><div className="service-metric"><strong>{analytics.eventsProcessed}</strong><span>patient events processed</span></div><div className="service-list">{analytics.patientEvents.slice(-3).reverse().map((event, index) => <div className="service-list-row" key={`${event.patientId}-${index}`}><span className="mini-avatar mint-avatar"><Icon name="activity" /></span><div><strong>{event.patientName}</strong><small>{event.eventType.replace('_', ' ')} · {event.email}</small></div><em>Consumed</em></div>)}{analytics.patientEvents.length === 0 && <p className="service-empty">Create a patient to publish a Kafka event.</p>}</div></article>
      </div>
    </section>
  )
}

function PatientTable({ patients, query, onQueryChange, onEdit, onDelete, loading }) {
  const filtered = useMemo(() => {
    const value = query.trim().toLowerCase()
    return patients.filter((patient) => !value || [patient.name, patient.email, patient.address].some((field) => field?.toLowerCase().includes(value)))
  }, [patients, query])

  return (
    <section className="records-section">
      <div className="section-heading"><div><p className="eyebrow">Patient directory</p><h2>All patients <span>{patients.length}</span></h2></div><button className="primary" onClick={() => onEdit(null)}><Icon name="plus" /> Add patient</button></div>
      <div className="toolbar"><div className="search"><Icon name="search" /><input value={query} onChange={(e) => onQueryChange(e.target.value)} placeholder="Search by name, email or address" /></div><span className="result-count">{filtered.length} {filtered.length === 1 ? 'record' : 'records'}</span></div>
      <div className="table-card">{loading ? <div className="empty-state"><div className="spinner" />Loading patient records…</div> : filtered.length === 0 ? <div className="empty-state"><div className="empty-icon"><Icon name="users" /></div><h3>{query ? 'No matching patients' : 'No patient records yet'}</h3><p>{query ? 'Try another search term.' : 'Add your first patient to start building the directory.'}</p></div> : <table><thead><tr><th>Patient</th><th>Contact</th><th>Date of birth</th><th>Address</th><th><span className="sr-only">Actions</span></th></tr></thead><tbody>{filtered.map((patient) => <tr key={patient.id}><td><div className="patient-cell"><span className="patient-avatar">{patient.name?.charAt(0).toUpperCase()}</span><div><strong>{patient.name}</strong><small>ID · {patient.id?.slice(0, 8)}</small></div></div></td><td>{patient.email}</td><td>{patient.dateOfBirth}</td><td className="address-cell">{patient.address}</td><td><div className="row-actions"><button className="icon-button" onClick={() => onEdit(patient)} aria-label={`Edit ${patient.name}`}><Icon name="edit" /></button><button className="icon-button danger" onClick={() => onDelete(patient)} aria-label={`Delete ${patient.name}`}><Icon name="trash" /></button></div></td></tr>)}</tbody></table>}</div>
    </section>
  )
}

export default function Dashboard({ onLogout }) {
  const [patients, setPatients] = useState([])
  const [billingAccounts, setBillingAccounts] = useState([])
  const [analytics, setAnalytics] = useState({ eventsProcessed: 0, patientEvents: [], status: 'CONNECTING' })
  const [query, setQuery] = useState('')
  const [modalPatient, setModalPatient] = useState(null)
  const [modalOpen, setModalOpen] = useState(false)
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState('')
  const [notice, setNotice] = useState('')

  async function loadPatients() {
    setLoading(true)
    try {
      setPatients(await patientApi.list())
    } catch (err) {
      if (err.message.includes('(401)')) onLogout()
      else setError(err.message)
    } finally {
      setLoading(false)
    }
  }

  async function loadServiceData() {
    const [billing, analyticsResult] = await Promise.allSettled([
      serviceApi.billingAccounts(),
      serviceApi.analyticsOverview(),
    ])
    if (billing.status === 'fulfilled') setBillingAccounts(billing.value)
    if (analyticsResult.status === 'fulfilled') setAnalytics(analyticsResult.value)
    const failed = [billing, analyticsResult].find((result) => result.status === 'rejected')
    if (failed) setError(`Service data unavailable: ${failed.reason.message}`)
  }

  useEffect(() => {
    loadPatients()
    loadServiceData()
    const serviceRefresh = window.setInterval(loadServiceData, 5000)
    return () => window.clearInterval(serviceRefresh)
  }, [])

  async function savePatient(patient) {
    const editing = Boolean(patient.id)
    const body = { name: patient.name, email: patient.email, address: patient.address, dateOfBirth: patient.dateOfBirth }
    if (!editing) body.registeredDate = patient.registeredDate
    await (editing ? patientApi.update({ ...body, id: patient.id }) : patientApi.create(body))
    await Promise.all([loadPatients(), loadServiceData()])
    setNotice(editing ? 'Patient record updated.' : 'Patient added successfully.')
    setTimeout(() => setNotice(''), 3000)
  }

  async function removePatient(patient) {
    if (!window.confirm(`Delete ${patient.name}'s record? This action cannot be undone.`)) return
    try {
      await patientApi.remove(patient.id)
      setPatients((current) => current.filter((item) => item.id !== patient.id))
      setNotice('Patient record deleted.')
      setTimeout(() => setNotice(''), 3000)
    } catch (err) {
      setError(err.message)
    }
  }

  const openModal = (patient) => {
    setModalPatient(patient)
    setModalOpen(true)
  }

  return (
    <div className="app-shell">
      <aside className="sidebar">
        <div className="logo-line"><div className="brand-mark small"><Icon name="activity" /></div><strong>Careflow</strong></div>
        <div className="sidebar-label">Workspace</div>
        <nav><button className="nav-item active"><Icon name="grid" /> Overview</button><button className="nav-item"><Icon name="users" /> Patients <span className="nav-count">{patients.length}</span></button></nav>
        <div className="sidebar-bottom"><div className="security-note"><span>✓</span><div><strong>Protected workspace</strong><small>Your records are secure</small></div></div><button className="nav-item logout" onClick={onLogout}><Icon name="logout" /> Sign out</button></div>
      </aside>
      <main className="content">
        <header className="topbar"><div><p className="eyebrow">Monday, 18 October 2026</p><h1>Good morning, care team</h1></div><div className="avatar">A</div></header>
        <section className="stats-grid"><div className="stat-card"><div className="stat-icon blue"><Icon name="users" /></div><div><span>Total patients</span><strong>{patients.length}</strong><small>Active records</small></div></div><div className="stat-card"><div className="stat-icon mint"><Icon name="activity" /></div><div><span>Workspace status</span><strong className="status-text">Healthy</strong><small>All systems operational</small></div></div><div className="stat-card accent-card"><span>Keep your records up to date</span><strong>Every detail matters.</strong><button onClick={() => openModal(null)}>Add a patient <span>→</span></button></div></section>
        <ServicePanels billingAccounts={billingAccounts} analytics={analytics} />
        {notice && <div className="alert success">{notice}</div>}
        {error && <div className="alert error">{error}</div>}
        <PatientTable patients={patients} query={query} onQueryChange={setQuery} onEdit={openModal} onDelete={removePatient} loading={loading} />
      </main>
      {modalOpen && <PatientModal patient={modalPatient} onClose={() => setModalOpen(false)} onSave={savePatient} />}
    </div>
  )
}
