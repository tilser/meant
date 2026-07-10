import { useEffect, useMemo, useRef, useState } from 'react'

import {
  DEFAULT_SHARE_PERCENT,
  DEFAULT_TAKE_RATE_PERCENT,
  MARKET_ANCHOR_GMV,
  MODEL_ASSUMPTION_LABEL,
  calculateRevenueScenario,
  formatCompactUsd,
  pitchSourceGroups,
  pitchSources,
  revenueScenarioPresets,
  type PitchSource,
} from './pitchModel'
import './pitch.css'

const chapters = [
  { id: 'future', label: 'Future' },
  { id: 'present', label: 'Present' },
  { id: 'meant', label: 'Meant' },
  { id: 'lifecycle', label: 'Lifecycle' },
  { id: 'moat', label: 'Moat' },
  { id: 'platform', label: 'Platform' },
  { id: 'potential', label: 'Potential' },
] as const

type ChapterId = (typeof chapters)[number]['id']

const futureFragments = [
  'Running shoes · 8 stores',
  'Carry-on · 214 offers',
  'Refill · low stock',
  'Size 42 · remembered',
  'Warranty · 21 days',
  'Cotton · no polyester',
  'Delivery · before Friday',
  'Charger · already owned',
  'Return · outcome saved',
  'Coffee · best offer',
  'Headphones · compare 12',
  'Lamp · warm light',
  'Jacket · waterproof',
  'Desk · fits 120 cm',
  'Filters · compatible',
  'Price · under €100',
  'Merchant · trusted',
  'Resale · value known',
] as const

const lifecycleSteps = [
  'Discover',
  'Decide',
  'Buy',
  'Deliver',
  'Own',
  'Maintain',
  'Replenish',
  'Return / Resell',
  'Replace',
] as const

const ownershipSignals = [
  'orders',
  'inventory',
  'sizes + fit',
  'warranties',
  'consumables',
  'compatibility',
  'maintenance',
  'return windows',
  'delivery',
  'satisfaction',
  'replacement timing',
  'resale context',
] as const

const lifecycleMoments = [
  'These filters fit; remember the size.',
  'The detergent is running low; reorder the best offer.',
  'The warranty expires in 21 days.',
  'You already own the compatible charger.',
  'This purchase was returned; reduce future ranking.',
] as const

const flywheelStages = [
  'Better intent understanding',
  'Better product + offer ranking',
  'Higher completion + trust',
  'Richer ownership + outcome data',
  'A better next decision',
] as const

const moatAssets = [
  'Personal preference graph',
  'Ownership + outcome graph',
  'Cross-merchant product + offer identity graph',
  'Merchant capability + integration graph',
  'Checkout + reliability execution data',
] as const

export function PitchDeck() {
  const [activeChapter, setActiveChapter] = useState<ChapterId>('future')
  const [sharePercent, setSharePercent] = useState(DEFAULT_SHARE_PERCENT)
  const [takeRatePercent, setTakeRatePercent] = useState(DEFAULT_TAKE_RATE_PERCENT)
  const activeIndexRef = useRef(0)
  const sourceDialogRef = useRef<HTMLDialogElement>(null)
  const sourceTriggerRef = useRef<HTMLButtonElement>(null)

  const activeIndex = chapters.findIndex(({ id }) => id === activeChapter)
  const revenue = useMemo(
    () =>
      calculateRevenueScenario({
        marketGmv: MARKET_ANCHOR_GMV,
        sharePercent,
        takeRatePercent,
      }),
    [sharePercent, takeRatePercent],
  )

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
        className={chapterClass('future', 'pcos-future')}
        data-pitch-chapter="future"
        id="future"
        aria-labelledby="future-title"
      >
        <div className="pcos-chapter-inner pcos-future-layout">
          <div className="pcos-heading pcos-future-copy">
            <p className="pcos-kicker">2030 / The interface shift</p>
            <h1 id="future-title">Commerce is becoming intent, not navigation.</h1>
            <div className="pcos-hero-proof">
              <strong>$3T–$5T</strong>
              <p>
                of global consumer commerce could be mediated by agents in 2030—even under moderate
                scenarios.
              </p>
              <SourceLink source={pitchSources.mckinseyArticle} />
            </div>
          </div>

          <figure className="pcos-intent-figure" aria-labelledby="intent-caption">
            <div className="pcos-fragment-field" aria-hidden="true">
              {futureFragments.map((fragment) => (
                <span className="pcos-fragment" key={fragment}>
                  {fragment}
                </span>
              ))}
            </div>
            <div className="pcos-intent-object">
              <span>One person · one intent</span>
              <p>“Find the carry-on that fits my trips, my budget, and what I already own.”</p>
              <div>
                <i />
                Meant is considering 214 offers across compatible merchants
              </div>
            </div>
            <figcaption id="intent-caption">
              Storefronts become supply. Intent becomes the interface.
            </figcaption>
          </figure>

          <div className="pcos-future-evidence" aria-label="Supporting evidence">
            <article>
              <strong>$900B–$1T</strong>
              <p>US B2C goods commerce by 2030. Services and significant B2B are excluded.</p>
              <SourceLink source={pitchSources.mckinseyReport} />
            </article>
            <article>
              <strong>+693.4%</strong>
              <p>
                YoY GenAI referral traffic to US retail sites in the 2025 holiday season. The base
                remains modest.
              </p>
              <SourceLink source={pitchSources.adobeHoliday} />
            </article>
          </div>
        </div>
      </section>

      <section
        className={chapterClass('present', 'pcos-present')}
        data-pitch-chapter="present"
        id="present"
        aria-labelledby="present-title"
      >
        <div className="pcos-chapter-inner pcos-split-heading">
          <div className="pcos-heading">
            <p className="pcos-kicker">Now / The protocol moment</p>
            <h2 id="present-title">The foundations are being laid at the same time.</h2>
          </div>

          <div className="pcos-rail-stack" aria-label="The emerging agentic commerce stack">
            <svg className="pcos-stack-lines" viewBox="0 0 760 430" aria-hidden="true">
              <path d="M70 58 C220 58 235 90 378 90 S545 58 690 58" />
              <path d="M70 142 C220 142 235 174 378 174 S545 142 690 142" />
              <path d="M70 226 C220 226 235 258 378 258 S545 226 690 226" />
              <path d="M70 310 C220 310 235 342 378 342 S545 310 690 310" />
              <path d="M378 58 L378 366" />
            </svg>

            <article className="pcos-rail-layer">
              <span>01 · Surfaces</span>
              <h3>Agentic shopping is already in market.</h3>
              <p>Amazon Buy for Me; ChatGPT product discovery with merchant-controlled checkout.</p>
              <div className="pcos-inline-sources">
                <SourceLink source={pitchSources.amazonBuyForMe} compact />
                <SourceLink source={pitchSources.openAiDiscovery} compact />
              </div>
            </article>

            <article className="pcos-rail-layer">
              <span>02 · Commerce language</span>
              <h3>UCP gives agents and merchants a common contract.</h3>
              <p>Developed with five commerce leaders and endorsed by 20+ ecosystem partners.</p>
              <SourceLink source={pitchSources.googleUcp} compact />
            </article>

            <article className="pcos-rail-layer">
              <span>03 · Catalog + checkout</span>
              <h3>Shopify exposes discovery through embedded checkout.</h3>
              <p>Global Catalog, cart, checkout, Checkout Kit, ECP, completion, and orders.</p>
              <div className="pcos-inline-sources">
                <SourceLink source={pitchSources.shopifyAgents} compact />
                <SourceLink source={pitchSources.shopifyCheckoutKit} compact />
              </div>
            </article>

            <article className="pcos-rail-layer">
              <span>04 · Authorization</span>
              <h3>AP2 makes intent and cart approval verifiable.</h3>
              <p>60+ organizations; signed mandates create an authorization trail.</p>
              <SourceLink source={pitchSources.googleAp2} compact />
            </article>

            <article className="pcos-rail-layer pcos-trust-layer">
              <span>05 · Agent trust</span>
              <h3>Payment networks are identifying trusted agents.</h3>
              <p>Visa Trusted Agent Protocol and Mastercard Agent Pay.</p>
              <div className="pcos-inline-sources">
                <SourceLink source={pitchSources.visaTrustedAgent} compact />
                <SourceLink source={pitchSources.mastercardAgentPay} compact />
              </div>
            </article>
          </div>

          <p className="pcos-bridge">
            Standards make transactions possible. <strong>They do not know the buyer.</strong>
          </p>
        </div>
      </section>

      <section
        className={chapterClass('meant', 'pcos-meant')}
        data-pitch-chapter="meant"
        id="meant"
        aria-labelledby="meant-title"
      >
        <div className="pcos-chapter-inner pcos-split-heading">
          <div className="pcos-heading">
            <p className="pcos-kicker">The missing layer</p>
            <h2 id="meant-title">Meant is the Personal Commerce OS.</h2>
            <p className="pcos-lede">
              One person. Every compatible merchant. One continuous commerce memory.
            </p>
            <div className="pcos-inline-sources">
              <SourceLink source={pitchSources.meantReadme} />
              <SourceLink source={pitchSources.meantPlan} />
            </div>
          </div>

          <div
            className="pcos-os-diagram"
            aria-label="The three layers of the Personal Commerce OS"
          >
            <div className="pcos-os-person">
              <span>You</span>
              <p>Intent + durable context</p>
            </div>
            <div className="pcos-os-layer pcos-os-surfaces">
              <div>
                <span>Commerce surfaces</span>
                <strong>Meant web + mobile</strong>
              </div>
              <div>
                <strong>APIs + SDKs</strong>
                <span>Third-party agents</span>
              </div>
            </div>
            <div className="pcos-os-layer pcos-os-intelligence">
              <span>Commerce intelligence</span>
              <p>
                intent · preferences · product identity · grouped offers · personalization · ranking
                · explanations
              </p>
            </div>
            <div className="pcos-os-layer pcos-os-runtime">
              <span>Commerce runtime</span>
              <p>
                UCP core · provider extensions · auth · cart · embedded checkout · completion ·
                orders · fallbacks · observability
              </p>
              <small>Provider-specific complexity stops here</small>
            </div>
            <div className="pcos-os-providers" aria-label="Compatible provider rails">
              <span>Shopify</span>
              <span>UCP merchant</span>
              <span>Provider extension</span>
              <span>Future rail</span>
            </div>
          </div>
        </div>
      </section>

      <section
        className={chapterClass('lifecycle', 'pcos-lifecycle')}
        data-pitch-chapter="lifecycle"
        id="lifecycle"
        aria-labelledby="lifecycle-title"
      >
        <div className="pcos-chapter-inner">
          <div className="pcos-heading pcos-heading-row">
            <div>
              <p className="pcos-kicker">Beyond checkout</p>
              <h2 id="lifecycle-title">Checkout is not the end. It is the first durable signal.</h2>
            </div>
            <SourceLink source={pitchSources.meantReadme} />
          </div>

          <div className="pcos-lifecycle-layout">
            <figure className="pcos-lifecycle-orbit" aria-labelledby="lifecycle-caption">
              <div className="pcos-orbit-rings" aria-hidden="true">
                <i />
                <i />
              </div>
              <ol>
                {lifecycleSteps.map((step, index) => (
                  <li key={step}>
                    <span>{String(index + 1).padStart(2, '0')}</span>
                    {step}
                  </li>
                ))}
              </ol>
              <div className="pcos-ownership-core">
                <span>Living ownership graph</span>
                <div>
                  {ownershipSignals.map((signal) => (
                    <small key={signal}>{signal}</small>
                  ))}
                </div>
              </div>
              <figcaption id="lifecycle-caption">
                Each outcome changes the next discovery, decision, and execution.
              </figcaption>
            </figure>

            <div className="pcos-lifecycle-moments" aria-label="Lifecycle examples">
              <p className="pcos-moment-intro">Meant remembers what the receipt cannot.</p>
              {lifecycleMoments.map((moment, index) => (
                <blockquote key={moment}>
                  <span>{index < 2 ? 'Meant' : 'Ownership signal'}</span>
                  {moment}
                </blockquote>
              ))}
            </div>
          </div>
        </div>
      </section>

      <section
        className={chapterClass('moat', 'pcos-moat')}
        data-pitch-chapter="moat"
        id="moat"
        aria-labelledby="moat-title"
      >
        <div className="pcos-chapter-inner pcos-split-heading">
          <div className="pcos-heading">
            <p className="pcos-kicker">The compounding advantage</p>
            <h2 id="moat-title">Every completed lifecycle makes Meant harder to replace.</h2>
            <p className="pcos-lede">
              The moat is normalized compatibility + longitudinal buyer context + execution
              outcomes—not the LLM, and not connector count alone.
            </p>
            <div className="pcos-principles">
              <span>Privacy-controlled</span>
              <span>Provider-neutral</span>
            </div>
            <div className="pcos-inline-sources">
              <SourceLink source={pitchSources.meantReadme} />
              <SourceLink source={pitchSources.meantPlan} />
            </div>
          </div>

          <div className="pcos-moat-visual">
            <figure className="pcos-flywheel" aria-label="Meant data flywheel">
              <svg viewBox="0 0 520 520" aria-hidden="true">
                <defs>
                  <marker
                    id="pcos-arrow"
                    markerHeight="8"
                    markerWidth="8"
                    orient="auto"
                    refX="6"
                    refY="3"
                  >
                    <path d="M0,0 L0,6 L7,3 z" />
                  </marker>
                </defs>
                <path d="M260 54 A206 206 0 0 1 456 196" markerEnd="url(#pcos-arrow)" />
                <path d="M466 245 A206 206 0 0 1 356 433" markerEnd="url(#pcos-arrow)" />
                <path d="M316 458 A206 206 0 0 1 106 389" markerEnd="url(#pcos-arrow)" />
                <path d="M74 348 A206 206 0 0 1 86 140" markerEnd="url(#pcos-arrow)" />
                <path d="M121 104 A206 206 0 0 1 239 55" markerEnd="url(#pcos-arrow)" />
              </svg>
              <ol>
                {flywheelStages.map((stage, index) => (
                  <li key={stage}>
                    <span>{index + 1}</span>
                    {stage}
                  </li>
                ))}
              </ol>
              <div>
                <span>Outcome</span>
                <strong>becomes context</strong>
              </div>
            </figure>

            <ol className="pcos-asset-ledger" aria-label="Five compounding assets">
              {moatAssets.map((asset, index) => (
                <li key={asset}>
                  <span>{String(index + 1).padStart(2, '0')}</span>
                  {asset}
                </li>
              ))}
            </ol>
          </div>
        </div>
      </section>

      <section
        className={chapterClass('platform', 'pcos-platform')}
        data-pitch-chapter="platform"
        id="platform"
        aria-labelledby="platform-title"
      >
        <div className="pcos-chapter-inner">
          <div className="pcos-heading pcos-heading-row">
            <div>
              <p className="pcos-kicker">Consumer product + infrastructure</p>
              <h2 id="platform-title">One commerce runtime. Every shopping surface.</h2>
            </div>
            <div className="pcos-inline-sources">
              <SourceLink source={pitchSources.meantReadme} />
              <SourceLink source={pitchSources.googleUcp} />
            </div>
          </div>

          <div
            className="pcos-runtime-map"
            aria-label="One runtime powering consumer and infrastructure products"
          >
            <div className="pcos-runtime-surface pcos-consumer-surface">
              <span>Meant consumer OS · free to the user</span>
              <strong>Discover → cart → embedded checkout → lifecycle</strong>
              <small>Personalized for one continuous buyer</small>
            </div>
            <div className="pcos-runtime-surface pcos-agent-surface">
              <span>Meant infrastructure</span>
              <strong>APIs + SDKs for third-party agents</strong>
              <small>Discovery, identity, ranking, checkout, and orders</small>
            </div>

            <div className="pcos-runtime-flow pcos-flow-in" aria-hidden="true">
              <i />
              <i />
            </div>

            <div className="pcos-runtime-core">
              <span>Shared headless core</span>
              <strong>Meant Commerce Runtime</strong>
              <div>
                <small>intent</small>
                <small>offers</small>
                <small>capabilities</small>
                <small>execution</small>
                <small>outcomes</small>
              </div>
            </div>

            <div className="pcos-runtime-flow pcos-flow-out" aria-hidden="true">
              <i />
              <i />
              <i />
            </div>

            <div className="pcos-provider-rails">
              <span>Shopify</span>
              <span>Generic UCP</span>
              <span>Provider extensions</span>
              <p>Merchants remain merchants of record.</p>
            </div>

            <aside className="pcos-business-model">
              <span>Business model</span>
              <p>
                <strong>Consumer:</strong> commissions, affiliate, or execution revenue on completed
                purchases.
              </p>
              <p>
                <strong>Infrastructure:</strong> API, usage, or execution fees from third-party
                agents.
              </p>
              <small>No current contracts or revenue implied.</small>
            </aside>
          </div>
        </div>
      </section>

      <section
        className={chapterClass('potential', 'pcos-potential')}
        data-pitch-chapter="potential"
        id="potential"
        aria-labelledby="potential-title"
      >
        <div className="pcos-chapter-inner pcos-potential-layout">
          <div className="pcos-heading">
            <p className="pcos-kicker">The outcome</p>
            <h2 id="potential-title">0.1% can be a category-defining company.</h2>
            <div className="pcos-market-anchor">
              <span>Market-model anchor</span>
              <strong>$4T</strong>
              <p>Midpoint of McKinsey’s $3T–$5T 2030 global estimate.</p>
              <SourceLink source={pitchSources.mckinseyArticle} />
            </div>
          </div>

          <div className="pcos-model" aria-label="Illustrative revenue model">
            <div className="pcos-model-label">{MODEL_ASSUMPTION_LABEL}</div>
            <div className="pcos-scenario-presets" aria-label="Scenario presets">
              {revenueScenarioPresets.map((preset) => {
                const presetResult = calculateRevenueScenario({
                  marketGmv: MARKET_ANCHOR_GMV,
                  sharePercent: preset.sharePercent,
                  takeRatePercent: DEFAULT_TAKE_RATE_PERCENT,
                })
                const selected =
                  sharePercent === preset.sharePercent &&
                  takeRatePercent === DEFAULT_TAKE_RATE_PERCENT
                return (
                  <button
                    aria-pressed={selected}
                    className={selected ? 'is-selected' : ''}
                    key={preset.id}
                    onClick={() => {
                      setSharePercent(preset.sharePercent)
                      setTakeRatePercent(DEFAULT_TAKE_RATE_PERCENT)
                    }}
                    type="button"
                  >
                    <span>{preset.label}</span>
                    <strong>{formatPercent(preset.sharePercent)} share</strong>
                    <small>
                      {formatCompactUsd(presetResult.gmv)} GMV →{' '}
                      {formatCompactUsd(presetResult.revenue)} revenue
                    </small>
                  </button>
                )
              })}
            </div>

            <div className="pcos-model-result" aria-live="polite">
              <p>
                <strong>{formatPercent(sharePercent)}</strong> of{' '}
                {formatCompactUsd(MARKET_ANCHOR_GMV)}
              </p>
              <span>=</span>
              <div>
                <strong>{formatCompactUsd(revenue.gmv)}</strong>
                <small>agent-mediated GMV</small>
              </div>
              <span>× {formatPercent(takeRatePercent)}</span>
              <div className="pcos-revenue-result">
                <strong>{formatCompactUsd(revenue.revenue)}</strong>
                <small>illustrative annual revenue</small>
              </div>
            </div>

            <div className="pcos-model-controls">
              <label>
                <span>
                  GMV share assumption <strong>{formatPercent(sharePercent)}</strong>
                </span>
                <input
                  aria-label="GMV share assumption"
                  max="0.5"
                  min="0.02"
                  onChange={(event) => setSharePercent(Number(event.currentTarget.value))}
                  step="0.01"
                  type="range"
                  value={sharePercent}
                />
              </label>
              <label>
                <span>
                  Blended transaction take assumption{' '}
                  <strong>{formatPercent(takeRatePercent)}</strong>
                </span>
                <input
                  aria-label="Blended transaction take assumption"
                  max="5"
                  min="1"
                  onChange={(event) => setTakeRatePercent(Number(event.currentTarget.value))}
                  step="0.25"
                  type="range"
                  value={takeRatePercent}
                />
              </label>
            </div>
            <p className="pcos-model-note">
              Share and take rate are Meant assumptions, not sourced facts or a forecast.
              Infrastructure revenue is upside and is not included.
            </p>
          </div>

          <blockquote className="pcos-closing-line">
            Meant does not need to own commerce. It needs to become the layer commerce flows
            through.
          </blockquote>
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
            External facts link to direct primary sources. Market share and take-rate inputs are
            explicitly labeled as Meant model assumptions.
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

function scrollToChapter(index: number) {
  const chapter = chapters[index]
  if (!chapter) return
  const reduceMotion = window.matchMedia('(prefers-reduced-motion: reduce)').matches
  document
    .getElementById(chapter.id)
    ?.scrollIntoView({ behavior: reduceMotion ? 'auto' : 'smooth', block: 'start' })
}

function formatPercent(value: number) {
  return `${value.toLocaleString('en-US', { maximumFractionDigits: 2, minimumFractionDigits: value < 1 ? 2 : 0 })}%`
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
