import { createFileRoute } from '@tanstack/react-router'

export const Route = createFileRoute('/')({
  component: Home,
})

function Home() {
  return (
    <main className="shell">
      <section className="workspace">
        <div className="intro">
          <p className="eyebrow">Meant</p>
          <h1>Capture what matters, then turn it into motion.</h1>
          <p className="summary">
            A focused workspace for decisions, context, and follow-through.
          </p>
        </div>

        <div className="status-grid" aria-label="Project status">
          <div>
            <span>Backend</span>
            <strong>Spring Boot</strong>
          </div>
          <div>
            <span>Database</span>
            <strong>Postgres</strong>
          </div>
          <div>
            <span>Frontend</span>
            <strong>TanStack</strong>
          </div>
        </div>
      </section>
    </main>
  )
}
