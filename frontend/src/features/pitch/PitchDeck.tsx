import { useEffect, useMemo, useRef, useState } from 'react'
import type { CSSProperties } from 'react'

import {
  MODEL_ASSUMPTION_LABEL,
  calculateCommerceScenario,
  commerceScenarioPresets,
  formatCompactUsd,
  pitchSourceGroups,
  pitchSources,
  type CommerceScenarioId,
  type PitchSource,
} from './pitchModel'
import './pitch.css'

const chapters = [
  { id: 'future', label: 'Future' },
  { id: 'present', label: 'Present' },
  { id: 'meant', label: 'Meant' },
  { id: 'platform', label: 'Platform' },
  { id: 'demo', label: 'Demo' },
  { id: 'moat', label: 'Moat' },
  { id: 'risk', label: 'ChatGPT' },
  { id: 'business', label: 'Business model' },
  { id: 'outcome', label: 'Outcome' },
] as const

type ChapterId = (typeof chapters)[number]['id']

const futureLifecycle = [
  ['Intent', 'A need, not a search query'],
  ['Discovery', 'Every compatible source'],
  ['Decision', 'One explainable choice'],
  ['Transaction', 'Authorized execution'],
  ['Ownership', 'A durable relationship'],
  ['Next action', 'Maintain, return, resell, replenish'],
] as const

const meantLifecycle = [
  'Understand',
  'Discover',
  'Resolve',
  'Decide',
  'Buy',
  'Own',
  'Maintain / Return / Resell / Replenish',
] as const

const coreCapabilities = [
  'Commerce integration layer',
  'Product & offer graph',
  'Decision and ranking engine',
  'Transaction orchestration',
  'Personal ownership graph',
] as const

const moatLayers = [
  {
    title: 'Merchant integration graph',
    body: 'Protocols, provider-specific extensions, and observed merchant behavior.',
  },
  {
    title: 'Product and offer graph',
    body: 'Same-product identity across merchants, with offer history and availability.',
  },
  {
    title: 'Execution intelligence',
    body: 'Which checkout rail works, real completion rates, and operational reliability.',
  },
  {
    title: 'Personal ownership graph',
    body: 'What each person owns, uses, returns, replaces, or regularly replenishes.',
  },
  {
    title: 'Trust and preference intelligence',
    body: 'Why a person decides—not only what they clicked—and their durable constraints.',
  },
] as const

const monetizationLayers = [
  ['Transaction revenue', 'Revenue on commerce Meant successfully executes.'],
  ['Agent infrastructure', 'Usage and execution fees from agents and partners.'],
  ['Premium ownership', 'Optional consumer services after the purchase.'],
  ['Merchant products', 'Later: execution quality and intelligence tools.'],
] as const

const demoSteps = ['Need', 'Search', 'Resolve', 'Decide', 'Buy', 'Own'] as const

export function PitchDeck() {
  const [activeChapter, setActiveChapter] = useState<ChapterId>('future')
  const [scenarioId, setScenarioId] = useState<CommerceScenarioId>('scale')
  const [demoStep, setDemoStep] = useState(0)
  const activeIndexRef = useRef(0)
  const sourceDialogRef = useRef<HTMLDialogElement>(null)
  const sourceTriggerRef = useRef<HTMLButtonElement>(null)

  const activeIndex = chapters.findIndex(({ id }) => id === activeChapter)
  const scenario =
    commerceScenarioPresets.find((candidate) => candidate.id === scenarioId) ??
    commerceScenarioPresets[1]
  const economics = useMemo(() => calculateCommerceScenario(scenario), [scenario])

  useEffect(() => {
    activeIndexRef.current = activeIndex
  }, [activeIndex])

  useEffect(() => {
    const sections = Array.from(document.querySelectorAll<HTMLElement>('[data-pitch-chapter]'))
    const ratios = new Map<Element, number>()
    const observer = new IntersectionObserver(
      (entries) => {
        for (const entry of entries) ratios.set(entry.target, entry.intersectionRatio)
        const mostVisible = sections.reduce<HTMLElement | null>((best, section) => {
          if (!best) return section
          return (ratios.get(section) ?? 0) > (ratios.get(best) ?? 0) ? section : best
        }, null)
        if (!mostVisible || (ratios.get(mostVisible) ?? 0) <= 0.12) return
        const chapter = mostVisible.dataset.pitchChapter as ChapterId | undefined
        if (chapter) setActiveChapter(chapter)
      },
      { threshold: [0, 0.12, 0.3, 0.5, 0.72, 0.9] },
    )

    sections.forEach((section) => observer.observe(section))

    const handleKeyDown = (event: KeyboardEvent) => {
      if (sourceDialogRef.current?.open) return
      const target = event.target as HTMLElement | null
      if (target?.closest('input, select, textarea, button, a, [contenteditable="true"]')) return

      const forwardKeys = ['ArrowDown', 'ArrowRight', 'PageDown']
      const backwardKeys = ['ArrowUp', 'ArrowLeft', 'PageUp']
      let nextIndex: number | null = null
      if (forwardKeys.includes(event.key)) nextIndex = activeIndexRef.current + 1
      if (backwardKeys.includes(event.key)) nextIndex = activeIndexRef.current - 1
      if (event.key === 'Home') nextIndex = 0
      if (event.key === 'End') nextIndex = chapters.length - 1
      if (nextIndex === null) return

      event.preventDefault()
      scrollToChapter(Math.max(0, Math.min(chapters.length - 1, nextIndex)))
    }

    window.addEventListener('keydown', handleKeyDown)
    return () => {
      observer.disconnect()
      window.removeEventListener('keydown', handleKeyDown)
    }
  }, [])

  const chapterClass = (id: ChapterId, extra = '') =>
    `pcos-chapter ${extra} ${activeChapter === id ? 'is-active' : ''}`.trim()
  const openSources = () => sourceDialogRef.current?.showModal()
  const closeSources = () => sourceDialogRef.current?.close()

  return (
    <main className="pcos-deck" tabIndex={-1}>
      <header className="pcos-topbar">
        <a className="pcos-brand" href="/" aria-label="Meant home">
          <img src="/assets/meant-logo.png" alt="Meant" />
        </a>

        <nav className="pcos-progress" aria-label="Investor deck chapters">
          <span className="pcos-progress-count" aria-live="polite">
            {String(activeIndex + 1).padStart(2, '0')} / {String(chapters.length).padStart(2, '0')}
          </span>
          <span className="pcos-progress-track" aria-hidden="true">
            <span style={{ width: `${((activeIndex + 1) / chapters.length) * 100}%` }} />
          </span>
          <span className="pcos-progress-dots">
            {chapters.map((chapter, index) => (
              <button
                aria-current={activeChapter === chapter.id ? 'step' : undefined}
                aria-label={`Go to ${chapter.label}, chapter ${index + 1}`}
                className={activeChapter === chapter.id ? 'is-current' : ''}
                key={chapter.id}
                onClick={() => scrollToChapter(index)}
                type="button"
              />
            ))}
          </span>
        </nav>

        <button
          className="pcos-sources-trigger"
          onClick={openSources}
          ref={sourceTriggerRef}
          type="button"
        >
          Sources
        </button>
      </header>

      <section
        aria-labelledby="future-title"
        className={chapterClass('future', 'pcos-future')}
        data-pitch-chapter="future"
        id="future"
      >
        <div className="pcos-chapter-inner pcos-future-layout">
          <div className="pcos-heading pcos-future-copy">
            <p className="pcos-kicker">01 / Future — buying becomes delegated</p>
            <h1 id="future-title">AI agents will manage what people buy.</h1>
            <p className="pcos-lede">
              From the first expression of intent to everything that follows ownership.
            </p>
          </div>

          <div className="pcos-delegation-flow" aria-label="The future buying lifecycle">
            {futureLifecycle.map(([title, body], index) => (
              <article key={title} style={{ '--step': index } as CSSProperties}>
                <span>{String(index + 1).padStart(2, '0')}</span>
                <strong>{title}</strong>
                <small>{body}</small>
              </article>
            ))}
          </div>

          <div className="pcos-future-evidence" aria-label="Evidence for the shift">
            <article>
              <strong>$3T–$5T</strong>
              <p>of global consumer commerce could be mediated by agents in 2030.</p>
              <SourceLink source={pitchSources.mckinseyArticle} />
            </article>
            <article>
              <strong>+693.4%</strong>
              <p>YoY GenAI referral traffic to US retail sites in the 2025 holiday season.</p>
              <SourceLink source={pitchSources.adobeHoliday} />
            </article>
          </div>

          <blockquote className="pcos-slide-thesis">
            Discovery becomes delegated. The transaction becomes software. Ownership becomes data.
          </blockquote>
        </div>
      </section>

      <section
        aria-labelledby="present-title"
        className={chapterClass('present', 'pcos-present')}
        data-pitch-chapter="present"
        id="present"
      >
        <div className="pcos-chapter-inner pcos-present-layout">
          <div className="pcos-heading pcos-heading-row">
            <div>
              <p className="pcos-kicker">02 / Present — the rails are being laid</p>
              <h2 id="present-title">Commerce is becoming agent-ready—and fragmenting first.</h2>
            </div>
            <p className="pcos-side-note">
              Standards solve communication. They do not yet create one continuous commerce system.
            </p>
          </div>

          <div className="pcos-ecosystem-map" aria-label="The emerging commerce rails">
            <article>
              <span>Protocols</span>
              <strong>UCP</strong>
              <p>A common contract for agents and merchants.</p>
              <SourceLink source={pitchSources.googleUcp} compact />
            </article>
            <article>
              <span>Agentic storefronts</span>
              <strong>ChatGPT · Amazon</strong>
              <p>New shopping surfaces are already reaching consumers.</p>
              <div className="pcos-inline-sources">
                <SourceLink source={pitchSources.openAiDiscovery} compact />
                <SourceLink source={pitchSources.amazonBuyForMe} compact />
              </div>
            </article>
            <article>
              <span>Provider extensions</span>
              <strong>Catalog · ECP · Checkout Kit</strong>
              <p>Real execution remains provider-specific.</p>
              <SourceLink source={pitchSources.shopifyAgents} compact />
            </article>
            <article>
              <span>Authorization + trust</span>
              <strong>AP2 · Visa · Mastercard</strong>
              <p>New rails establish identity, intent, and permission.</p>
              <SourceLink source={pitchSources.googleAp2} compact />
            </article>

            <div className="pcos-missing-system">
              <span>Still disconnected</span>
              <ul>
                <li>All merchants + provider differences</li>
                <li>Product identity + offers</li>
                <li>Checkout execution</li>
                <li>Long-term ownership lifecycle</li>
              </ul>
            </div>
          </div>

          <blockquote className="pcos-slide-thesis">
            The protocols are arriving. <strong>The Personal Commerce OS is still missing.</strong>
          </blockquote>
        </div>
      </section>

      <section
        aria-labelledby="meant-title"
        className={chapterClass('meant', 'pcos-meant')}
        data-pitch-chapter="meant"
        id="meant"
      >
        <div className="pcos-chapter-inner pcos-meant-layout">
          <div className="pcos-heading">
            <p className="pcos-kicker">03 / Meant — the Personal Commerce OS</p>
            <h2 id="meant-title">One system for everything people buy and own.</h2>
          </div>

          <p className="pcos-definition">
            Meant understands what you need, finds the best product and offer, completes the
            transaction, and manages everything you own.
          </p>

          <div className="pcos-meant-lifecycle" aria-label="The Meant commerce lifecycle">
            {meantLifecycle.map((step, index) => (
              <div className={index === meantLifecycle.length - 1 ? 'is-aftercare' : ''} key={step}>
                <span>{String(index + 1).padStart(2, '0')}</span>
                <strong>{step}</strong>
              </div>
            ))}
          </div>

          <div className="pcos-not-this" aria-label="What Meant is and is not">
            <span>Not a marketplace</span>
            <span>Not another shopping chatbot</span>
            <strong>A personal commerce operating system</strong>
          </div>

          <div className="pcos-inline-sources">
            <SourceLink source={pitchSources.meantReadme} />
            <SourceLink source={pitchSources.meantPlan} />
          </div>
        </div>
      </section>

      <section
        aria-labelledby="platform-title"
        className={chapterClass('platform', 'pcos-platform')}
        data-pitch-chapter="platform"
        id="platform"
      >
        <div className="pcos-chapter-inner pcos-platform-layout">
          <div className="pcos-heading pcos-heading-row">
            <div>
              <p className="pcos-kicker">04 / One platform. Two interfaces.</p>
              <h2 id="platform-title">One commerce operating system, exposed two ways.</h2>
            </div>
            <SourceLink source={pitchSources.meantPlan} />
          </div>

          <div className="pcos-interface-map" aria-label="One Meant platform with two interfaces">
            <article className="pcos-interface pcos-interface-people">
              <span>Interface 01</span>
              <strong>Meant for people</strong>
              <p>A super shopping app and Personal Commerce OS.</p>
              <small>Intent in → best decision → lifelong ownership context</small>
            </article>

            <div className="pcos-core">
              <span>Shared Meant core</span>
              <strong>Commerce OS</strong>
              <ol>
                {coreCapabilities.map((capability) => (
                  <li key={capability}>{capability}</li>
                ))}
              </ol>
            </div>

            <article className="pcos-interface pcos-interface-agents">
              <span>Interface 02</span>
              <strong>Meant for agents</strong>
              <p>One API for every supported merchant.</p>
              <small>Any agent in → normalized commerce execution out</small>
            </article>
          </div>

          <blockquote className="pcos-slide-thesis">
            We are not building two products. We are exposing one commerce operating system through
            two interfaces.
          </blockquote>
        </div>
      </section>

      <section
        aria-labelledby="demo-title"
        className={chapterClass('demo', 'pcos-demo')}
        data-pitch-chapter="demo"
        id="demo"
      >
        <div className="pcos-chapter-inner pcos-demo-layout">
          <div className="pcos-heading pcos-demo-heading">
            <p className="pcos-kicker">05 / See Meant work</p>
            <h2 id="demo-title">One need. One continuous transaction.</h2>
            <p className="pcos-lede">A guided, self-contained demo—no live backend required.</p>
          </div>

          <DemoPrototype step={demoStep} onStepChange={setDemoStep} />
        </div>
      </section>

      <section
        aria-labelledby="moat-title"
        className={chapterClass('moat', 'pcos-moat')}
        data-pitch-chapter="moat"
        id="moat"
      >
        <div className="pcos-chapter-inner pcos-moat-layout">
          <div className="pcos-heading pcos-heading-row">
            <div>
              <p className="pcos-kicker">06 / Moat — five compounding layers</p>
              <h2 id="moat-title">Every transaction makes Meant harder to replicate.</h2>
            </div>
            <p className="pcos-side-note">
              Integrations are the starting point. Learning across execution and ownership is the
              moat.
            </p>
          </div>

          <div className="pcos-moat-stack" aria-label="Five layers of Meant's moat">
            {moatLayers.map((layer, index) => (
              <article key={layer.title} style={{ '--layer': index } as CSSProperties}>
                <span>{String(index + 1).padStart(2, '0')}</span>
                <div>
                  <strong>{layer.title}</strong>
                  <p>{layer.body}</p>
                </div>
              </article>
            ))}
          </div>

          <blockquote className="pcos-moat-contrast">
            <span>Search data explains interest.</span>
            <strong>Meant learns intent, transactions, and ownership.</strong>
          </blockquote>
        </div>
      </section>

      <section
        aria-labelledby="risk-title"
        className={chapterClass('risk', 'pcos-risk')}
        data-pitch-chapter="risk"
        id="risk"
      >
        <div className="pcos-chapter-inner pcos-risk-layout">
          <div className="pcos-heading">
            <p className="pcos-kicker">07 / The platform question</p>
            <h2 id="risk-title">What if ChatGPT builds this?</h2>
          </div>

          <div className="pcos-risk-answer">
            <p>
              <span>If Meant is only a shopping interface,</span>
              <strong>ChatGPT can replace us.</strong>
            </p>
            <p>
              <span>If Meant becomes the commerce operating system,</span>
              <strong>ChatGPT becomes a distribution channel.</strong>
            </p>
          </div>

          <div className="pcos-role-map" aria-label="Roles in agentic commerce">
            <article>
              <span>General agents own</span>
              <strong>Conversation + broad intelligence</strong>
            </article>
            <article className="is-meant">
              <span>Meant owns</span>
              <strong>Commerce abstraction + resolution + execution + ownership</strong>
            </article>
            <article>
              <span>Merchants own</span>
              <strong>Supply + fulfillment + merchant relationship</strong>
            </article>
          </div>

          <div className="pcos-risk-note">
            <p>
              <strong>The risk is real:</strong> OpenAI or another platform can vertically
              integrate. Our strategy is agent neutrality, ecosystem breadth, execution quality, and
              a direct user relationship.
            </p>
            <SourceLink source={pitchSources.openAiDiscovery} />
          </div>

          <blockquote className="pcos-slide-thesis">
            Every AI becoming a shopping agent expands the market for Meant’s infrastructure.
          </blockquote>
        </div>
      </section>

      <section
        aria-labelledby="business-title"
        className={chapterClass('business', 'pcos-business')}
        data-pitch-chapter="business"
        id="business"
      >
        <div className="pcos-chapter-inner pcos-business-layout">
          <div className="pcos-heading pcos-heading-row">
            <div>
              <p className="pcos-kicker">08 / Business model — monetizing the lifecycle</p>
              <h2 id="business-title">Aligned revenue at every layer of commerce.</h2>
            </div>
            <p className="pcos-ranking-promise">
              <span>Non-negotiable</span>
              Monetization never changes user-aligned ranking.
            </p>
          </div>

          <div className="pcos-monetization-grid">
            {monetizationLayers.map(([title, body], index) => (
              <article key={title}>
                <span>{String(index + 1).padStart(2, '0')}</span>
                <strong>{title}</strong>
                <p>{body}</p>
              </article>
            ))}
          </div>

          <div className="pcos-bottom-up-model" aria-label="Illustrative bottom-up revenue model">
            <div className="pcos-model-header">
              <span>{MODEL_ASSUMPTION_LABEL}</span>
              <div>
                {commerceScenarioPresets.map((preset) => (
                  <button
                    aria-pressed={scenarioId === preset.id}
                    className={scenarioId === preset.id ? 'is-selected' : ''}
                    key={preset.id}
                    onClick={() => setScenarioId(preset.id)}
                    type="button"
                  >
                    {preset.label}
                  </button>
                ))}
              </div>
            </div>

            <div className="pcos-formula-grid">
              <article>
                <span>Consumer scenario</span>
                <p>
                  <strong>{formatCount(scenario.activeUsers)}</strong> users ×{' '}
                  <strong>{scenario.purchasesPerUser}</strong> purchases ×{' '}
                  <strong>${scenario.gmvPerPurchase}</strong> GMV
                </p>
                <div>
                  <span>{formatCompactUsd(economics.consumerGmv)} GMV</span>
                  <strong>{formatCompactUsd(economics.transactionRevenue)} revenue</strong>
                  <small>
                    at {formatPercent(scenario.monetizationPercent)} effective monetization
                  </small>
                </div>
              </article>
              <article>
                <span>Agent / API scenario</span>
                <p>
                  <strong>{formatCount(scenario.agentTransactions)}</strong> executions ×{' '}
                  <strong>${scenario.infrastructureFee.toFixed(2)}</strong> fee
                </p>
                <div>
                  <span>Infrastructure</span>
                  <strong>{formatCompactUsd(economics.infrastructureRevenue)} revenue</strong>
                  <small>usage and execution only</small>
                </div>
              </article>
              <div className="pcos-total-revenue">
                <span>Illustrative annual revenue</span>
                <strong>{formatCompactUsd(economics.totalRevenue)}</strong>
              </div>
            </div>
          </div>
        </div>
      </section>

      <section
        aria-labelledby="outcome-title"
        className={chapterClass('outcome', 'pcos-outcome')}
        data-pitch-chapter="outcome"
        id="outcome"
      >
        <div className="pcos-chapter-inner pcos-outcome-layout">
          <div className="pcos-heading pcos-outcome-heading">
            <p className="pcos-kicker">09 / The outcome</p>
            <h2 id="outcome-title">The default commerce layer for people and agents.</h2>
          </div>

          <p className="pcos-outcome-definition">
            One commerce operating system between every person, every agent, and every merchant.
          </p>

          <div
            className="pcos-outcome-map"
            aria-label="People and agents connect to merchants through Meant"
          >
            <div>
              <span>People</span>
              <span>AI agents</span>
            </div>
            <i aria-hidden="true">→</i>
            <strong>Meant</strong>
            <i aria-hidden="true">→</i>
            <div>
              <span>Global merchants</span>
              <span>Every commerce rail</span>
            </div>
          </div>

          <div className="pcos-outcome-economics" aria-live="polite">
            <article>
              <span>Annual GMV orchestrated</span>
              <strong>{formatCompactUsd(economics.consumerGmv)}</strong>
            </article>
            <article>
              <span>Annual revenue</span>
              <strong>{formatCompactUsd(economics.totalRevenue)}</strong>
            </article>
            <p>
              Bottom-up {scenario.label.toLowerCase()} scenario: {formatCount(scenario.activeUsers)}{' '}
              active consumers + {formatCount(scenario.agentTransactions)} agent executions.{' '}
              <button onClick={() => scrollToChapter(7)} type="button">
                View assumptions
              </button>
            </p>
          </div>

          <blockquote className="pcos-closing-line">
            Meant does not end at checkout. It becomes the intelligence layer for everything people
            buy and own.
          </blockquote>
          <SourceLink source={pitchSources.mckinseyArticle} />
        </div>
      </section>

      <dialog
        aria-labelledby="pcos-sources-title"
        className="pcos-sources-dialog"
        onClick={(event) => {
          if (event.target === event.currentTarget) closeSources()
        }}
        onClose={() => sourceTriggerRef.current?.focus()}
        onKeyDown={(event) => {
          if (event.key === 'Escape') {
            event.preventDefault()
            closeSources()
          }
        }}
        ref={sourceDialogRef}
      >
        <div className="pcos-sources-drawer">
          <header>
            <div>
              <p className="pcos-kicker">Primary material</p>
              <h2 id="pcos-sources-title">Sources</h2>
            </div>
            <button aria-label="Close sources" onClick={closeSources} type="button">
              <CloseIcon />
            </button>
          </header>
          <p className="pcos-sources-intro">
            External facts link to direct sources. All bottom-up economic inputs are explicitly
            labeled as Meant assumptions rather than forecasts.
          </p>
          <div className="pcos-source-groups">
            {pitchSourceGroups.map((group) => (
              <div className="pcos-source-group" key={group.title}>
                <h3>{group.title}</h3>
                {group.sources.map((source) => (
                  <a href={source.href} key={source.id} rel="noreferrer" target="_blank">
                    <span>{source.title}</span>
                    <p>{source.note}</p>
                    <small>{new URL(source.href).hostname.replace('www.', '')}</small>
                  </a>
                ))}
              </div>
            ))}
          </div>
        </div>
      </dialog>
    </main>
  )
}

function DemoPrototype({
  step,
  onStepChange,
}: Readonly<{ step: number; onStepChange: (step: number) => void }>) {
  const next = () => onStepChange(Math.min(demoSteps.length - 1, step + 1))

  return (
    <div className="pcos-prototype">
      <header>
        <div>
          <i aria-hidden="true" />
          <span>Meant guided demo</span>
          <small>offline</small>
        </div>
        <button onClick={() => onStepChange(0)} type="button">
          Reset
        </button>
      </header>

      <nav aria-label="Demo progress">
        {demoSteps.map((label, index) => (
          <button
            aria-current={index === step ? 'step' : undefined}
            className={index <= step ? 'is-reached' : ''}
            key={label}
            onClick={() => onStepChange(index)}
            type="button"
          >
            <span>{index + 1}</span>
            {label}
          </button>
        ))}
      </nav>

      <div className="pcos-prototype-screen" aria-live="polite">
        {step === 0 && (
          <div className="pcos-demo-need">
            <span className="pcos-demo-eyebrow">Tell Meant what you need</span>
            <blockquote>
              “I need a carry-on for weekly work trips. It must fit European cabins, roll quietly,
              and arrive before Friday. Under €300.”
            </blockquote>
            <div>
              <span>Cabin compatible</span>
              <span>Quiet wheels</span>
              <span>Delivery constraint</span>
              <span>Budget €300</span>
            </div>
            <button className="pcos-demo-primary" onClick={next} type="button">
              Find the right carry-on <span>→</span>
            </button>
          </div>
        )}

        {step === 1 && (
          <div className="pcos-demo-search">
            <div className="pcos-demo-activity">
              <span className="pcos-demo-eyebrow">Searching across supported commerce</span>
              {['Shopify Global Catalog', 'Direct UCP merchants', 'Provider-specific catalogs'].map(
                (source, index) => (
                  <p key={source}>
                    <i style={{ '--delay': index } as CSSProperties} />
                    {source}
                    <strong>{[128, 61, 25][index]} offers</strong>
                  </p>
                ),
              )}
            </div>
            <div className="pcos-demo-result-count">
              <strong>214</strong>
              <span>offers found</span>
              <small>Grouped into 37 distinct products</small>
            </div>
            <button className="pcos-demo-primary" onClick={next} type="button">
              Resolve duplicate products <span>→</span>
            </button>
          </div>
        )}

        {step === 2 && (
          <div className="pcos-demo-resolve">
            <span className="pcos-demo-eyebrow">Same product, resolved across merchants</span>
            <article>
              <div className="pcos-product-visual">A</div>
              <div>
                <span>Best match</span>
                <strong>Arlo Carry-On 40L</strong>
                <p>55 × 35 × 23 cm · 2.8 kg · lifetime shell warranty</p>
              </div>
              <ul>
                <li>
                  <span>Travel House</span>
                  <strong>€269 · Thu</strong>
                </li>
                <li>
                  <span>Arlo direct</span>
                  <strong>€279 · Wed</strong>
                </li>
                <li>
                  <span>Bag World</span>
                  <strong>€259 · Mon</strong>
                </li>
              </ul>
            </article>
            <button className="pcos-demo-primary" onClick={next} type="button">
              Choose the best offer <span>→</span>
            </button>
          </div>
        )}

        {step === 3 && (
          <div className="pcos-demo-decide">
            <span className="pcos-demo-eyebrow">Meant recommendation</span>
            <div className="pcos-recommendation-card">
              <div>
                <span>Best overall offer</span>
                <strong>Arlo direct · €279</strong>
                <p>Not the lowest price. The best fit for this intent.</p>
              </div>
              <ol>
                <li>
                  <span>01</span>Arrives Wednesday—two days before the trip
                </li>
                <li>
                  <span>02</span>Verified European cabin dimensions
                </li>
                <li>
                  <span>03</span>Direct warranty registration and free returns
                </li>
              </ol>
            </div>
            <button className="pcos-demo-primary" onClick={next} type="button">
              Buy without leaving Meant <span>→</span>
            </button>
          </div>
        )}

        {step === 4 && (
          <div className="pcos-demo-buy">
            <span className="pcos-demo-eyebrow">Secure embedded checkout</span>
            <div className="pcos-checkout-card">
              <div>
                <span>Arlo Carry-On 40L</span>
                <strong>€279.00</strong>
              </div>
              <p>
                <span>Deliver to</span>
                <strong>David · Prague 2</strong>
              </p>
              <p>
                <span>Arrival</span>
                <strong>Wednesday, 15 July</strong>
              </p>
              <p>
                <span>Payment</span>
                <strong>•••• 2048</strong>
              </p>
              <small>Sold and fulfilled by Arlo. Merchant remains merchant of record.</small>
            </div>
            <button className="pcos-demo-primary" onClick={next} type="button">
              Authorize purchase · €279 <span>→</span>
            </button>
          </div>
        )}

        {step === 5 && (
          <div className="pcos-demo-own">
            <div className="pcos-success-mark" aria-hidden="true">
              ✓
            </div>
            <span className="pcos-demo-eyebrow">Purchase complete</span>
            <h3>Arlo Carry-On is now in your ownership hub.</h3>
            <div>
              <span>Delivery tracked</span>
              <span>Warranty registered</span>
              <span>Return window watched</span>
              <span>Resale value tracked</span>
            </div>
            <blockquote>
              This is where commerce normally ends. <strong>For Meant, it begins.</strong>
            </blockquote>
            <button className="pcos-demo-secondary" onClick={() => onStepChange(0)} type="button">
              Run demo again
            </button>
          </div>
        )}
      </div>
    </div>
  )
}

function scrollToChapter(index: number) {
  const chapter = chapters[index]
  if (!chapter) return
  const reduceMotion = window.matchMedia('(prefers-reduced-motion: reduce)').matches
  document
    .getElementById(chapter.id)
    ?.scrollIntoView({ behavior: reduceMotion ? 'auto' : 'smooth', block: 'start' })
}

function formatPercent(value: number) {
  return `${value.toLocaleString('en-US', { maximumFractionDigits: 2 })}%`
}

function formatCount(value: number) {
  return new Intl.NumberFormat('en-US', {
    compactDisplay: 'short',
    maximumFractionDigits: 1,
    notation: 'compact',
  }).format(value)
}

function SourceLink({
  source,
  compact = false,
}: Readonly<{ source: PitchSource; compact?: boolean }>) {
  return (
    <a
      aria-label={`Source: ${source.title}`}
      className={`pcos-source-link ${compact ? 'is-compact' : ''}`}
      href={source.href}
      rel="noreferrer"
      target="_blank"
    >
      <span aria-hidden="true">↗</span>
      {source.label}
    </a>
  )
}

function CloseIcon() {
  return (
    <svg aria-hidden="true" viewBox="0 0 24 24">
      <path d="M6 6l12 12M18 6L6 18" />
    </svg>
  )
}
