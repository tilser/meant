import { type PointerEvent as ReactPointerEvent, useEffect, useRef, useState } from 'react'

import { ChevronIcon } from '../../shared/icons'
import { SparkMark } from '../../shared/ui'
import { useStoredState } from '../../shared/storage'

const WORKBENCH_MIN_WIDTH = 300
const WORKBENCH_MAX_WIDTH = 640

function clampNumber(value: number, min: number, max: number): number {
  return Math.min(Math.max(value, min), max)
}

function AgentIcon({ size = 14 }: Readonly<{ size?: number }>) {
  return (
    <svg width={size} height={size} viewBox="0 0 18 18" fill="none" aria-hidden>
      <rect
        x="3.5"
        y="5"
        width="11"
        height="8.5"
        rx="2.2"
        stroke="currentColor"
        strokeWidth="1.4"
      />
      <path d="M9 2.5V5" stroke="currentColor" strokeWidth="1.4" strokeLinecap="round" />
      <circle cx="9" cy="2" r="1" fill="currentColor" />
      <circle cx="7" cy="9" r="1" fill="currentColor" />
      <circle cx="11" cy="9" r="1" fill="currentColor" />
    </svg>
  )
}

export function Workbench() {
  const [open, setOpen] = useState(false)
  const [width, setWidth] = useStoredState('meant.workbench.width', 360)
  const resizeCleanupRef = useRef<(() => void) | null>(null)
  const safeWidth = clampNumber(width, WORKBENCH_MIN_WIDTH, WORKBENCH_MAX_WIDTH)

  useEffect(() => {
    document.documentElement.style.setProperty('--ins-w', `${safeWidth}px`)
  }, [safeWidth])

  useEffect(() => {
    document.body.classList.toggle('insights-open', open)
    return () => document.body.classList.remove('insights-open')
  }, [open])

  useEffect(
    () => () => {
      resizeCleanupRef.current?.()
    },
    [],
  )

  const startResize = (event: ReactPointerEvent<HTMLDivElement>) => {
    if (event.button !== 0) {
      return
    }
    event.preventDefault()
    resizeCleanupRef.current?.()
    document.body.classList.add('mt-ins-resizing')
    const move = (moveEvent: PointerEvent) => {
      setWidth(clampNumber(moveEvent.clientX, WORKBENCH_MIN_WIDTH, WORKBENCH_MAX_WIDTH))
    }
    const cleanup = () => {
      window.removeEventListener('pointermove', move)
      window.removeEventListener('pointerup', cleanup)
      window.removeEventListener('pointercancel', cleanup)
      document.body.classList.remove('mt-ins-resizing')
      resizeCleanupRef.current = null
    }
    window.addEventListener('pointermove', move)
    window.addEventListener('pointerup', cleanup, { once: true })
    window.addEventListener('pointercancel', cleanup, { once: true })
    resizeCleanupRef.current = cleanup
  }

  return (
    <>
      <button
        className="mt-ins-tab"
        type="button"
        onClick={() => setOpen((current) => !current)}
        title="Workbench - coming soon"
        aria-label={open ? 'Hide workbench' : 'Show workbench'}
        aria-expanded={open}
      >
        <SparkMark size={15} />
        <span className="mt-ins-tab-label mt-mono" aria-hidden="true">
          Workbench
        </span>
      </button>

      <aside className={`mt-ins ${open ? 'open' : ''}`} style={{ width: `${safeWidth}px` }}>
        <div
          className="mt-ins-resize"
          onPointerDown={startResize}
          title="Drag to resize the workbench"
        >
          <span />
        </div>
        <div className="mt-shelf-head">
          <div className="mt-shelf-title-wrap">
            <span className="mt-shelf-title">Workbench</span>
            <span className="mt-mono mt-shelf-sub">Insights &amp; agents</span>
          </div>
          <div className="mt-shelf-head-tools">
            <button
              className="mt-shelf-close"
              type="button"
              onClick={() => setOpen(false)}
              title="Hide workbench"
              aria-label="Hide workbench"
            >
              <ChevronIcon direction="left" size={14} />
            </button>
          </div>
        </div>

        <div className="mt-shelf-body mt-ins-coming">
          <section className="mt-ins-coming-card" aria-labelledby="workbench-coming-title">
            <span className="mt-ins-coming-badge mt-mono">
              <SparkMark size={11} /> Coming soon
            </span>
            <span className="mt-ins-coming-icon">
              <AgentIcon size={28} />
            </span>
            <h2 id="workbench-coming-title">Build your own product research team</h2>
            <p className="mt-ins-coming-copy">
              The Workbench will let you start your own sub-agents to research products in parallel.
              As they work, Meant will automatically collect their findings here and turn them into
              useful product insights.
            </p>

            <div className="mt-ins-coming-features">
              <div className="mt-ins-coming-feature">
                <span className="mt-ins-coming-feature-icon">
                  <AgentIcon />
                </span>
                <span>
                  <strong>Start sub-agents</strong>
                  <small>Assign focused product research tasks from one place.</small>
                </span>
              </div>
              <div className="mt-ins-coming-feature">
                <span className="mt-ins-coming-feature-icon">
                  <SparkMark size={14} />
                </span>
                <span>
                  <strong>Get automatic insights</strong>
                  <small>
                    See useful findings and product signals as agents finish their work.
                  </small>
                </span>
              </div>
            </div>

            <p className="mt-ins-coming-note">
              This feature is not available yet. Agent controls and product insights will appear
              here when the Workbench launches.
            </p>
          </section>
        </div>
      </aside>
    </>
  )
}
