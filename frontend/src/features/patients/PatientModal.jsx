import { useState } from 'react'
import { EMPTY_PATIENT } from '../../constants'
import Icon from '../../components/Icon'

export default function PatientModal({ patient, onClose, onSave }) {
  const [form, setForm] = useState(patient || EMPTY_PATIENT)
  const [error, setError] = useState('')
  const [busy, setBusy] = useState(false)
  const editing = Boolean(patient?.id)

  async function submit(event) {
    event.preventDefault()
    setError('')
    setBusy(true)
    try {
      await onSave(form)
      onClose()
    } catch (err) {
      setError(err.message)
    } finally {
      setBusy(false)
    }
  }

  const update = (key) => (event) => setForm({ ...form, [key]: event.target.value })

  return (
    <div className="modal-backdrop" onMouseDown={(e) => e.target === e.currentTarget && onClose()}>
      <section className="modal">
        <div className="modal-heading"><div><p className="eyebrow">Patient record</p><h2>{editing ? 'Edit patient' : 'Add a patient'}</h2></div><button className="icon-button" onClick={onClose} aria-label="Close"><Icon name="close" /></button></div>
        <form onSubmit={submit} className="patient-form">
          <div className="form-grid">
            <label>Full name<input required maxLength="100" value={form.name} onChange={update('name')} placeholder="e.g. Maya Fernando" /></label>
            <label>Email address<input required type="email" value={form.email} onChange={update('email')} placeholder="patient@example.com" /></label>
            <label>Date of birth<input required type="date" value={form.dateOfBirth} onChange={update('dateOfBirth')} /></label>
            {!editing && <label>Registration date<input required type="date" value={form.registeredDate} onChange={update('registeredDate')} /></label>}
          </div>
          <label>Address<textarea required rows="3" value={form.address} onChange={update('address')} placeholder="Street, city, country" /></label>
          {error && <div className="alert error">{error}</div>}
          <div className="modal-actions"><button type="button" className="secondary" onClick={onClose}>Cancel</button><button className="primary" disabled={busy}>{busy ? 'Saving…' : editing ? 'Save changes' : 'Add patient'}</button></div>
        </form>
      </section>
    </div>
  )
}
