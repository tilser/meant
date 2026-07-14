import {
  type PointerEvent as ReactPointerEvent,
  useCallback,
  useEffect,
  useMemo,
  useRef,
  useState,
} from 'react'

import type { AskReplyDraft } from '../../ask/types'
import { DustingContainer } from '../../shared/DustingContainer'
import { ChevronIcon, OpenIcon } from '../../shared/icons'
import { CloseIcon, SparkMark } from '../../shared/ui'
import { useStoredState } from '../../shared/storage'
import type { Preference, Product } from '../../types'
import {
  buildMockWorkbenchInsights,
  MOCK_WORKBENCH_AGENTS,
  MOCK_WORKBENCH_INSIGHTS,
  mockWorkbenchAgentResult,
} from './mockData'
import type { WorkbenchAgent, WorkbenchInsight } from './types'

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

function ReplyIcon() {
  return (
    <svg width="12" height="12" viewBox="0 0 16 16" fill="none" aria-hidden>
      <path
        d="M6.5 4 3 7.5 6.5 11M3 7.5h6.5A3.5 3.5 0 0 1 13 11v1.5"
        stroke="currentColor"
        strokeWidth="1.5"
        strokeLinecap="round"
        strokeLinejoin="round"
      />
    </svg>
  )
}

function SendIcon() {
  return (
    <svg width="14" height="14" viewBox="0 0 18 18" fill="none" aria-hidden>
      <path
        d="M3.5 9h11M9.5 4l5 5-5 5"
        stroke="currentColor"
        strokeWidth="1.7"
        strokeLinecap="round"
        strokeLinejoin="round"
      />
    </svg>
  )
}

function useTypedText(text: string, enabled: boolean): string {
  const [length, setLength] = useState(enabled ? 0 : text.length)

  useEffect(() => {
    if (!enabled) {
      setLength(text.length)
      return undefined
    }
    setLength(0)
    let currentLength = 0
    let timeoutId: number | null = null
    const tick = () => {
      currentLength += 1
      setLength(currentLength)
      if (currentLength < text.length) {
        timeoutId = window.setTimeout(tick, 13)
      }
    }
    timeoutId = window.setTimeout(tick, 110)
    return () => {
      if (timeoutId !== null) {
        window.clearTimeout(timeoutId)
      }
    }
  }, [enabled, text])

  return text.slice(0, length)
}

function insightReplyDraft(insight: WorkbenchInsight): AskReplyDraft {
  return {
    id: `workbench-reply-${insight.id}`,
    label: insight.label,
    text: insight.text,
    suggestedText: insight.suggestedReply,
  }
}

function WorkbenchInsightCard({
  insight,
  onReply,
  onRemove,
}: Readonly<{
  insight: WorkbenchInsight
  onReply: (draft: AskReplyDraft) => void
  onRemove: (id: string) => void
}>) {
  const [removing, setRemoving] = useState(false)
  return (
    <DustingContainer dusting={removing} onGone={() => onRemove(insight.id)}>
      <article className={`mt-ins-card ${insight.fresh ? 'fresh' : ''}`}>
        <div className="mt-ins-key mt-mono">
          <span className="mt-ins-dot" />
          {insight.label}
        </div>
        <p className="mt-ins-text">{insight.text}</p>
        <div className="mt-ins-card-foot">
          <button
            className="mt-ins-reply"
            type="button"
            onClick={() => onReply(insightReplyDraft(insight))}
          >
            <ReplyIcon /> Reply in chat
          </button>
          <button
            className="mt-ins-dismiss"
            type="button"
            onClick={() => setRemoving(true)}
            title="Dismiss insight"
            aria-label={`Dismiss ${insight.label} insight`}
          >
            <CloseIcon size={11} />
          </button>
        </div>
      </article>
    </DustingContainer>
  )
}

function WorkbenchAgentCard({
  agent,
  onOpenInChat,
  onRemove,
}: Readonly<{
  agent: WorkbenchAgent
  onOpenInChat: (agent: WorkbenchAgent) => void
  onRemove: (id: string) => void
}>) {
  const [removing, setRemoving] = useState(false)
  return (
    <DustingContainer dusting={removing} onGone={() => onRemove(agent.id)}>
      <article className="mt-ins-agent-card">
        <div className="mt-ins-agent-head">
          <span className="mt-ins-agent-icon">
            <AgentIcon />
          </span>
          <span className="mt-ins-agent-task" title={agent.task}>
            {agent.task}
          </span>
          <button
            className="mt-ins-dismiss"
            type="button"
            onClick={() => setRemoving(true)}
            title="Dismiss agent"
            aria-label={`Dismiss agent researching ${agent.task}`}
          >
            <CloseIcon size={11} />
          </button>
        </div>
        {agent.status === 'working' ? (
          <div className="mt-ins-agent-status" role="status">
            <span className="mt-ins-spinner" aria-hidden /> Researching mock sources...
          </div>
        ) : (
          <>
            <p className="mt-ins-agent-result">{agent.result}</p>
            <div className="mt-ins-card-foot">
              {agent.openedInChat ? (
                <span className="mt-ins-asked">
                  <OpenIcon /> In chat
                </span>
              ) : (
                <button className="mt-ins-reply" type="button" onClick={() => onOpenInChat(agent)}>
                  <OpenIcon /> Open in chat
                </button>
              )}
            </div>
          </>
        )}
      </article>
    </DustingContainer>
  )
}

export function Workbench({
  product,
  query,
  preferences,
  onReply,
  onAgentReport,
}: Readonly<{
  product: Product | null
  query: string
  preferences: readonly Preference[]
  onReply: (draft: AskReplyDraft) => void
  onAgentReport: (task: string, result: string) => void
}>) {
  const [open, setOpen] = useState(false)
  const [peek, setPeek] = useState<WorkbenchInsight | null>(null)
  const [taskDraft, setTaskDraft] = useState('')
  const [clearing, setClearing] = useState(false)
  const [width, setWidth] = useStoredState('meant.workbench.width', 360)
  const [insights, setInsights] = useStoredState<WorkbenchInsight[]>(
    'meant.workbench.insights',
    () => [...MOCK_WORKBENCH_INSIGHTS],
  )
  const [agents, setAgents] = useStoredState<WorkbenchAgent[]>('meant.workbench.agents', () => [
    ...MOCK_WORKBENCH_AGENTS,
  ])
  const seenInsightIdsRef = useRef(new Set(insights.map((insight) => insight.id)))
  const resizeCleanupRef = useRef<(() => void) | null>(null)
  const peekTimerRef = useRef<number | null>(null)
  const safeWidth = clampNumber(width, WORKBENCH_MIN_WIDTH, WORKBENCH_MAX_WIDTH)
  const contextualInsights = useMemo(
    () => buildMockWorkbenchInsights(product, query, preferences),
    [preferences, product, query],
  )
  const peekText = useTypedText(peek?.text ?? '', Boolean(peek && !open))
  const count = insights.length + agents.length

  useEffect(() => {
    document.documentElement.style.setProperty('--ins-w', `${safeWidth}px`)
  }, [safeWidth])

  useEffect(() => {
    document.body.classList.toggle('insights-open', open)
    return () => document.body.classList.remove('insights-open')
  }, [open])

  useEffect(() => {
    for (const insight of insights) {
      seenInsightIdsRef.current.add(insight.id)
    }
  }, [insights])

  useEffect(() => {
    const fresh = contextualInsights.filter((insight) => !seenInsightIdsRef.current.has(insight.id))
    if (fresh.length === 0) {
      return
    }
    for (const insight of fresh) {
      seenInsightIdsRef.current.add(insight.id)
    }
    const stamped = fresh.map((insight) => ({ ...insight, fresh: true }))
    setInsights((current) =>
      [...stamped, ...current.map((insight) => ({ ...insight, fresh: false }))].slice(0, 40),
    )
    if (!open) {
      setPeek(stamped[0] ?? null)
      if (peekTimerRef.current !== null) {
        window.clearTimeout(peekTimerRef.current)
      }
      peekTimerRef.current = window.setTimeout(() => {
        setPeek(null)
        peekTimerRef.current = null
      }, 7000)
    }
  }, [contextualInsights, open, setInsights])

  useEffect(() => {
    const workingAgentIds = agents
      .filter((agent) => agent.status === 'working')
      .map((agent) => agent.id)
    if (workingAgentIds.length === 0) {
      return undefined
    }
    const timeoutId = window.setTimeout(() => {
      setAgents((current) =>
        current.map((agent) =>
          workingAgentIds.includes(agent.id) && agent.status === 'working'
            ? { ...agent, status: 'done', result: mockWorkbenchAgentResult(agent.task) }
            : agent,
        ),
      )
    }, 1600)
    return () => window.clearTimeout(timeoutId)
  }, [agents, setAgents])

  useEffect(
    () => () => {
      resizeCleanupRef.current?.()
      if (peekTimerRef.current !== null) {
        window.clearTimeout(peekTimerRef.current)
      }
    },
    [],
  )

  const openWorkbench = () => {
    setPeek(null)
    setOpen(true)
    setInsights((current) => current.map((insight) => ({ ...insight, fresh: false })))
  }

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

  const dispatchAgent = (task: string) => {
    const cleanTask = task.trim()
    if (!cleanTask) {
      return
    }
    const id = `mock-agent-${Date.now().toString(36)}`
    setTaskDraft('')
    setOpen(true)
    setAgents((current) => [{ id, task: cleanTask, status: 'working', result: '' }, ...current])
  }

  const removeInsight = useCallback(
    (id: string) => {
      setInsights((current) => current.filter((insight) => insight.id !== id))
      setPeek((current) => (current?.id === id ? null : current))
    },
    [setInsights],
  )

  const removeAgent = useCallback(
    (id: string) => {
      setAgents((current) => current.filter((agent) => agent.id !== id))
    },
    [setAgents],
  )

  const openAgentInChat = (agent: WorkbenchAgent) => {
    if (!agent.result) {
      return
    }
    onAgentReport(agent.task, agent.result)
    setAgents((current) =>
      current.map((candidate) =>
        candidate.id === agent.id ? { ...candidate, openedInChat: true } : candidate,
      ),
    )
  }

  const replyToInsight = (draft: AskReplyDraft) => {
    setPeek(null)
    onReply(draft)
  }

  return (
    <>
      <button
        className="mt-ins-tab"
        type="button"
        onClick={() => (open ? setOpen(false) : openWorkbench())}
        title="Workbench - insights and research agents"
        aria-label={open ? 'Hide workbench' : 'Show workbench'}
        aria-expanded={open}
      >
        <SparkMark size={15} />
        {count > 0 ? <span className="mt-ins-tab-count mt-mono">{count}</span> : null}
      </button>

      {peek && !open ? (
        <aside className="mt-ins-peek" aria-live="polite">
          <div className="mt-ins-peek-head">
            <span className="mt-ins-peek-name mt-mono">
              <span className="mt-ins-dot" /> Insight
            </span>
            <button
              className="mt-ins-peek-close"
              type="button"
              onClick={() => setPeek(null)}
              aria-label="Dismiss insight preview"
            >
              <CloseIcon size={11} />
            </button>
          </div>
          <div className="mt-ins-peek-key mt-mono">{peek.label}</div>
          <p className="mt-ins-peek-text">
            {peekText}
            {peekText.length < peek.text.length ? <span className="mt-ins-peek-caret" /> : null}
          </p>
          <div className="mt-ins-peek-actions">
            <button
              className="mt-ins-peek-open"
              type="button"
              onClick={() => replyToInsight(insightReplyDraft(peek))}
            >
              <ReplyIcon /> Reply in chat
            </button>
            <button className="mt-ins-peek-workbench" type="button" onClick={openWorkbench}>
              Open workbench
            </button>
          </div>
        </aside>
      ) : null}

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
            <span className="mt-mono mt-shelf-sub">Insights &amp; agents · mocked</span>
          </div>
          <div className="mt-shelf-head-tools">
            {count > 0 ? (
              <button
                className="mt-shelf-clear"
                type="button"
                onClick={() => setClearing(true)}
                disabled={clearing}
              >
                Clear
              </button>
            ) : null}
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
        <div className="mt-shelf-body">
          <form
            className="mt-ins-agentbar"
            onSubmit={(event) => {
              event.preventDefault()
              dispatchAgent(taskDraft)
            }}
          >
            <span className="mt-ins-agentbar-icon">
              <AgentIcon />
            </span>
            <input
              value={taskDraft}
              onChange={(event) => setTaskDraft(event.target.value)}
              placeholder="Send a mock agent to research..."
              aria-label="Research task"
            />
            <button type="submit" disabled={!taskDraft.trim()} aria-label="Dispatch research agent">
              <SendIcon />
            </button>
          </form>

          {count === 0 ? (
            <div className="mt-shelf-empty">
              <span className="mt-shelf-empty-mark">
                <SparkMark size={22} />
              </span>
              <p className="mt-shelf-empty-title">Your workbench is clear</p>
              <p className="mt-shelf-empty-sub">
                Search, open a product, or dispatch a mock agent. Meant will collect useful context
                here while the backend feature is being built.
              </p>
            </div>
          ) : (
            <DustingContainer
              className="mt-ins-clear-region"
              dusting={clearing}
              onGone={() => {
                setClearing(false)
                setInsights([])
                setAgents([])
                setPeek(null)
              }}
            >
              {agents.length > 0 ? <div className="mt-ins-section mt-mono">Agents</div> : null}
              {agents.map((agent) => (
                <WorkbenchAgentCard
                  key={agent.id}
                  agent={agent}
                  onOpenInChat={openAgentInChat}
                  onRemove={removeAgent}
                />
              ))}
              {insights.length > 0 ? <div className="mt-ins-section mt-mono">Insights</div> : null}
              {insights.map((insight) => (
                <WorkbenchInsightCard
                  key={insight.id}
                  insight={insight}
                  onReply={replyToInsight}
                  onRemove={removeInsight}
                />
              ))}
            </DustingContainer>
          )}
        </div>
      </aside>
    </>
  )
}
