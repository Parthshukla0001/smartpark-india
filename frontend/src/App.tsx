import './App.css'

function App() {
  return (
    <div style={{
      display: 'flex',
      flexDirection: 'column',
      alignItems: 'center',
      justifyContent: 'center',
      minHeight: '100vh',
      fontFamily: 'system-ui, -apple-system, sans-serif',
      backgroundColor: '#f8fafc',
      color: '#1e293b',
    }}>
      <h1 style={{ fontSize: '2.5rem', marginBottom: '0.5rem', color: '#0f172a' }}>
        🅿️ SmartPark India
      </h1>
      <p style={{ fontSize: '1.25rem', color: '#64748b' }}>
        Smart Parking Management Platform
      </p>
      <div style={{
        marginTop: '2rem',
        padding: '1rem 2rem',
        backgroundColor: '#e2e8f0',
        borderRadius: '8px',
        fontSize: '0.9rem',
        color: '#475569',
      }}>
        Phase 0 — Project Foundation ✅
      </div>
    </div>
  )
}

export default App
