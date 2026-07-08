import { useState } from 'react'

type PitchSource = {
  readonly label: string
  readonly href: string
}

type EvidenceCard = {
  readonly value: string
  readonly label: string
  readonly text: string
  readonly source: PitchSource
}

type MarketMove = {
  readonly layer: string
  readonly title: string
  readonly text: string
  readonly source: PitchSource
}

type MeantLayer = {
  readonly label: string
  readonly title: string
  readonly text: string
  readonly source: PitchSource
}

type DemoStep = {
  readonly id: string
  readonly label: string
  readonly title: string
  readonly prompt: string
  readonly agent: string
  readonly primary: string
  readonly secondary: string
  readonly metrics: readonly string[]
}

type RevenueScenario = {
  readonly name: string
  readonly share: string
  readonly gmv: string
  readonly takeRate: string
  readonly revenue: string
}

const SOURCES = {
  mckinseyViaIbd: {
    label: 'McKinsey forecast via Investors.com',
    href: 'https://www.investors.com/news/technology/shopify-stock-nrf-news-ai-agentic-shopping/',
  },
  adobeViaAxios: {
    label: 'Adobe holiday data via Axios',
    href: 'https://www.axios.com/2026/01/08/microsoft-ai-copilot-checkout',
  },
  ap2: {
    label: 'Google AP2 coverage via Axios',
    href: 'https://www.axios.com/2025/09/16/google-ai-agents-ecommerce-online-shopping',
  },
  aces: {
    label: 'ACES agentic e-commerce paper',
    href: 'https://arxiv.org/abs/2508.02630',
  },
  nrfForecast: {
    label: 'NRF 2025 retail forecast via Investopedia',
    href: 'https://www.investopedia.com/consumer-anxiety-could-mean-slower-retail-sales-growth-this-year-nrf-forecast-11707350',
  },
  openAiCheckout: {
    label: 'ChatGPT checkout via AP',
    href: 'https://apnews.com/article/3434f1b86b90b59de0baa43a8f28f380',
  },
  googleUcp: {
    label: 'Google UCP via The Verge',
    href: 'https://www.theverge.com/news/860446/google-ai-shopping-standard-buy-button-gemini',
  },
  walmartGemini: {
    label: 'Walmart + Gemini via Axios',
    href: 'https://www.axios.com/2026/01/11/walmart-google-gemini-ai-shopping',
  },
  amazonBuyForMe: {
    label: 'Amazon Buy for Me via The Verge',
    href: 'https://www.theverge.com/news/642947/amazon-ai-buy-products-other-websites',
  },
  visaTrustedAgent: {
    label: 'Visa Trusted Agent Protocol via Axios',
    href: 'https://www.axios.com/2025/10/14/visa-ai-shopping-agent-protocol-bot',
  },
  perplexityComet: {
    label: 'Perplexity Comet shopping via The Verge',
    href: 'https://www.theverge.com/news/813755/amazon-perplexity-ai-shopping-agent-block',
  },
  meantReadme: {
    label: 'Meant README',
    href: 'https://github.com/davidtilser/meant/blob/main/README.md',
  },
  meantUcpSpike: {
    label: 'Meant UCP checkout spike',
    href: 'https://github.com/davidtilser/meant/blob/main/docs/spikes/MEA-35-ucp-embedded-transport-feasibility.md',
  },
  ucpSpec: {
    label: 'UCP specification',
    href: 'https://ucp.dev/latest/specification/overview/',
  },
} satisfies Record<string, PitchSource>

const agendaItems = [
  'Prediction: buying becomes delegated',
  'Present: the ecosystem is already moving',
  'Meant: personal commerce OS + agent infrastructure',
  'Prototype: what the future feels like',
  'Potential: GMV, take rate, revenue',
  'Team: why we can own this layer',
] as const

const futureCards: readonly EvidenceCard[] = [
  {
    value: '$3T-5T',
    label: 'global annual agentic commerce by 2030',
    text:
      'McKinsey is cited as forecasting a multi-trillion-dollar agentic commerce opportunity as agents begin orchestrating buying journeys.',
    source: SOURCES.mckinseyViaIbd,
  },
  {
    value: '$1T',
    label: 'U.S. B2C retail potentially agent-orchestrated',
    text:
      'The same McKinsey-cited forecast frames U.S. consumer retail as large enough for agentic commerce to become its own distribution channel.',
    source: SOURCES.mckinseyViaIbd,
  },
  {
    value: '+693%',
    label: 'GenAI traffic to retail sites',
    text:
      'Adobe data cited by Axios shows generative-AI referral traffic to retail sites jumped during the 2025 holiday season.',
    source: SOURCES.adobeViaAxios,
  },
  {
    value: '60+',
    label: 'payment ecosystem partners around AP2',
    text:
      'Google AP2 is a signal that delegated buying needs proof of user intent, payment authorization, and agent accountability.',
    source: SOURCES.ap2,
  },
]

const presentMoves: readonly MarketMove[] = [
  {
    layer: 'Surface',
    title: 'ChatGPT is moving from answers to checkout.',
    text:
      'OpenAI introduced direct purchases in ChatGPT, starting with Etsy sellers and planned Shopify merchant support.',
    source: SOURCES.openAiCheckout,
  },
  {
    layer: 'Protocol',
    title: 'Google and Shopify are pushing UCP.',
    text:
      'Universal Commerce Protocol is being positioned as a shared language for agent-to-merchant discovery, checkout, and support.',
    source: SOURCES.googleUcp,
  },
  {
    layer: 'Retail',
    title: 'Walmart is putting shopping inside Gemini.',
    text:
      'Walmart announced Gemini integration so customers can discover products, build carts, and buy inside the assistant surface.',
    source: SOURCES.walmartGemini,
  },
  {
    layer: 'Marketplace',
    title: 'Amazon is testing off-Amazon buying.',
    text:
      'Buy for Me lets selected U.S. app users purchase products from third-party brand sites through an Amazon-managed handoff.',
    source: SOURCES.amazonBuyForMe,
  },
  {
    layer: 'Trust',
    title: 'Visa is building agent legitimacy rails.',
    text:
      'Visa Trusted Agent Protocol focuses on separating legitimate shopping agents from malicious bot traffic.',
    source: SOURCES.visaTrustedAgent,
  },
  {
    layer: 'Conflict',
    title: 'Perplexity shows the access fight is real.',
    text:
      'Comet can buy products for users, but Amazon objected, showing that merchant permissions and agent identity are unresolved.',
    source: SOURCES.perplexityComet,
  },
]

const meantLayers: readonly MeantLayer[] = [
  {
    label: 'Consumer OS',
    title: 'The buyer owns persistent preferences.',
    text:
      'Meant stores durable shopping constraints, taste, budget, saved products, inventory, cart, and order context.',
    source: SOURCES.meantReadme,
  },
  {
    label: 'Decision engine',
    title: 'Search becomes personalized ranking.',
    text:
      'Meant finds relevant merchants, searches products live, reranks candidates, and explains why each item matches.',
    source: SOURCES.meantReadme,
  },
  {
    label: 'Commerce rails',
    title: 'Agents need safe checkout primitives.',
    text:
      'The UCP path points to catalog, cart, checkout, order, identity-linking, and payment-handler orchestration.',
    source: SOURCES.meantUcpSpike,
  },
  {
    label: 'Infrastructure',
    title: 'Other agents can buy through Meant.',
    text:
      'Meant can expose buying capabilities to agents that need commerce execution without rebuilding merchant integrations.',
    source: SOURCES.ucpSpec,
  },
]

const demoSteps: readonly DemoStep[] = [
  {
    id: 'intent',
    label: 'Intent',
    title: 'User asks once.',
    prompt: 'Find me a durable organic cotton tee under $50. No polyester. Ships this week.',
    agent: 'Meant converts the ask into constraints, budget, delivery, merchant, and taste signals.',
    primary: 'Personal profile loaded',
    secondary: 'Organic cotton, natural materials, no polyester, value-first, highly rated',
    metrics: ['6 constraints', '4 preference groups', '1 buyer profile'],
  },
  {
    id: 'search',
    label: 'Search',
    title: 'Meant searches the merchant graph.',
    prompt: 'Searching merchants that can satisfy apparel + organic cotton + delivery constraints.',
    agent: 'The agent narrows the merchant set before product search, so results start relevant instead of broad.',
    primary: 'Field & Loom selected',
    secondary: 'Merchant profile matches sustainable apparel, stock availability, cart support',
    metrics: ['23 merchants scanned', '5 searched live', '41 candidates'],
  },
  {
    id: 'rank',
    label: 'Rank',
    title: 'Products are ranked against the person.',
    prompt: 'Heavyweight Organic Cotton Tee is the top match.',
    agent: 'Meant prefers exact materials, review quality, budget fit, and delivery speed over sponsored placement.',
    primary: '94% match',
    secondary: '100% organic cotton, GOTS certified, holds shape after wash, $38',
    metrics: ['$38 best offer', '4.7 rating', '3-day delivery'],
  },
  {
    id: 'checkout',
    label: 'Checkout',
    title: 'The agent prepares a safe purchase.',
    prompt: 'Add the best offer to cart and prepare checkout with user consent.',
    agent: 'Meant keeps payment and checkout state bounded: quote, consent, idempotency, and merchant handoff.',
    primary: 'Checkout ready',
    secondary: '1 item, $38 subtotal, delivery selected, buyer consent pending',
    metrics: ['Cart created', 'Quote verified', 'Consent required'],
  },
  {
    id: 'order',
    label: 'Order',
    title: 'Post-purchase becomes agent memory.',
    prompt: 'Track the order and remember what worked for the next purchase.',
    agent: 'Meant turns the completed purchase into inventory, order status, and better future recommendations.',
    primary: 'Order tracking active',
    secondary: 'ETA in 3 days, merchant order linked, preference outcome saved',
    metrics: ['Order event', 'Inventory signal', 'Preference feedback'],
  },
]

const revenueScenarios: readonly RevenueScenario[] = [
  {
    name: 'Conservative',
    share: '0.02%',
    gmv: '$200M',
    takeRate: '3%',
    revenue: '$6M',
  },
  {
    name: 'Base',
    share: '0.10%',
    gmv: '$1B',
    takeRate: '3%',
    revenue: '$30M',
  },
  {
    name: 'Upside',
    share: '0.50%',
    gmv: '$5B',
    takeRate: '3%',
    revenue: '$150M',
  },
]

const sourceGroups: readonly { readonly title: string; readonly sources: readonly PitchSource[] }[] = [
  {
    title: 'Future and market',
    sources: [
      SOURCES.mckinseyViaIbd,
      SOURCES.adobeViaAxios,
      SOURCES.ap2,
      SOURCES.aces,
      SOURCES.nrfForecast,
    ],
  },
  {
    title: 'What exists now',
    sources: [
      SOURCES.openAiCheckout,
      SOURCES.googleUcp,
      SOURCES.walmartGemini,
      SOURCES.amazonBuyForMe,
      SOURCES.visaTrustedAgent,
      SOURCES.perplexityComet,
    ],
  },
  {
    title: 'Meant and protocol',
    sources: [SOURCES.meantReadme, SOURCES.meantUcpSpike, SOURCES.ucpSpec],
  },
]

export function PitchDeck() {
  const [activeDemoStep, setActiveDemoStep] = useState(demoSteps[0])

  return (
    <main className="pitch-deck">
      <header className="pitch-topbar">
        <a className="pitch-brand" href="/">
          <img src="/assets/meant-logo.png" alt="Meant" />
          <span>Meant</span>
        </a>
        <nav className="pitch-nav" aria-label="Pitch deck">
          <a href="#future">Future</a>
          <a href="#present">Present</a>
          <a href="#meant">Meant</a>
          <a href="#demo">Prototype</a>
          <a href="#potential">Potential</a>
          <a href="#team">Team</a>
        </nav>
      </header>

      <section className="pitch-slide pitch-cover" aria-labelledby="cover-title">
        <div className="pitch-cover-copy">
          <p className="pitch-kicker">Investor pitch / Agentic commerce</p>
          <h1 id="cover-title">Meant is the personal commerce OS for the agent economy.</h1>
          <p>
            The next commerce interface is not a storefront. It is an agent that knows what the
            buyer means, can safely execute, and remembers the outcome.
          </p>
        </div>
        <div className="pitch-agenda" aria-label="Deck outline">
          {agendaItems.map((item, index) => (
            <a href={`#${['future', 'present', 'meant', 'demo', 'potential', 'team'][index]}`} key={item}>
              <span>{String(index + 1).padStart(2, '0')}</span>
              {item}
            </a>
          ))}
        </div>
      </section>

      <section className="pitch-slide pitch-evidence" id="future" aria-labelledby="future-title">
        <div className="pitch-section-head">
          <p className="pitch-kicker">01 / Prediction</p>
          <h2 id="future-title">Buying moves from search pages to delegated intent.</h2>
          <p>
            The user will stop browsing endless product grids. Agents will negotiate constraints,
            compare options, and complete purchases through trusted commerce rails.
          </p>
        </div>
        <div className="pitch-evidence-grid">
          {futureCards.map((card) => (
            <article className="pitch-evidence-card" key={card.value}>
              <strong>{card.value}</strong>
              <span>{card.label}</span>
              <p>{card.text}</p>
              <SourceLink source={card.source} />
            </article>
          ))}
        </div>
      </section>

      <section className="pitch-slide pitch-present" id="present" aria-labelledby="present-title">
        <div className="pitch-section-head">
          <p className="pitch-kicker">02 / Present</p>
          <h2 id="present-title">The category is already forming.</h2>
          <p>
            Surfaces, retailers, protocols, and payment networks are racing toward the same
            end-state: authenticated agents that can shop.
          </p>
        </div>
        <div className="pitch-market-stack">
          {presentMoves.map((move) => (
            <article className="pitch-market-card" key={move.title}>
              <span>{move.layer}</span>
              <div>
                <h3>{move.title}</h3>
                <p>{move.text}</p>
              </div>
              <SourceLink source={move.source} />
            </article>
          ))}
        </div>
      </section>

      <section className="pitch-slide pitch-meant" id="meant" aria-labelledby="meant-title">
        <div className="pitch-section-head">
          <p className="pitch-kicker">03 / Meant</p>
          <h2 id="meant-title">Meant owns the buyer layer and exposes the buying rails.</h2>
          <p>
            Meant starts as the consumer app people use to buy across the internet, then becomes the
            commerce infrastructure other agents call when they need to buy safely.
          </p>
        </div>
        <div className="pitch-architecture">
          <div className="pitch-architecture-core">
            <span>Personal commerce OS</span>
            <strong>Preference memory + merchant graph + checkout orchestration</strong>
          </div>
          {meantLayers.map((layer) => (
            <article className="pitch-layer-card" key={layer.title}>
              <span>{layer.label}</span>
              <h3>{layer.title}</h3>
              <p>{layer.text}</p>
              <SourceLink source={layer.source} />
            </article>
          ))}
        </div>
      </section>

      <section className="pitch-slide pitch-demo" id="demo" aria-labelledby="demo-title">
        <div className="pitch-section-head">
          <p className="pitch-kicker">04 / Prototype</p>
          <h2 id="demo-title">A future purchase with Meant.</h2>
          <p>
            Click through the flow. This is the same product logic Meant already has, compressed
            into the interaction an agentic shopper expects.
          </p>
        </div>
        <div className="pitch-demo-shell">
          <div className="pitch-demo-controls" role="tablist" aria-label="Prototype steps">
            {demoSteps.map((step) => (
              <button
                aria-selected={activeDemoStep.id === step.id}
                className={activeDemoStep.id === step.id ? 'active' : ''}
                key={step.id}
                onClick={() => setActiveDemoStep(step)}
                role="tab"
                type="button"
              >
                {step.label}
              </button>
            ))}
          </div>

          <div className="pitch-demo-board">
            <div className="pitch-demo-chat">
              <div className="pitch-chat-bubble user">
                <span>User</span>
                <p>{activeDemoStep.prompt}</p>
              </div>
              <div className="pitch-chat-bubble meant">
                <span>Meant</span>
                <p>{activeDemoStep.agent}</p>
              </div>
              <div className="pitch-agent-log">
                {activeDemoStep.metrics.map((metric) => (
                  <span key={metric}>{metric}</span>
                ))}
              </div>
            </div>

            <div className="pitch-demo-phone" aria-live="polite">
              <div className="pitch-phone-top">
                <span>Meant</span>
                <strong>{activeDemoStep.label}</strong>
              </div>
              <div className="pitch-product-visual">
                <span>{activeDemoStep.primary}</span>
              </div>
              <h3>{activeDemoStep.title}</h3>
              <p>{activeDemoStep.secondary}</p>
              <div className="pitch-phone-actions">
                <button type="button">Approve</button>
                <button type="button">Compare</button>
              </div>
            </div>
          </div>
        </div>
      </section>

      <section className="pitch-slide pitch-potential" id="potential" aria-labelledby="potential-title">
        <div className="pitch-section-head">
          <p className="pitch-kicker">05 / Potential</p>
          <h2 id="potential-title">A small share of agentic GMV is a large business.</h2>
          <p>
            Meant monetizes completed purchases through merchant commissions today, then can add
            infrastructure fees for agents that need catalog, cart, checkout, and order rails.
          </p>
        </div>
        <div className="pitch-potential-grid">
          <article className="pitch-market-size">
            <span>Market anchor</span>
            <strong>$1T</strong>
            <p>U.S. B2C retail potentially orchestrated by agents by 2030.</p>
            <SourceLink source={SOURCES.mckinseyViaIbd} />
          </article>
          <article className="pitch-market-size">
            <span>Current retail base</span>
            <strong>$5.42T-$5.48T</strong>
            <p>NRF forecast for 2025 U.S. core retail sales.</p>
            <SourceLink source={SOURCES.nrfForecast} />
          </article>
          <article className="pitch-market-size">
            <span>Monetization</span>
            <strong>GMV x take rate</strong>
            <p>Meant's current stated revenue model is merchant commissions on completed purchases.</p>
            <SourceLink source={SOURCES.meantReadme} />
          </article>
        </div>
        <div className="pitch-scenario-table" aria-label="Revenue scenarios">
          <div className="pitch-scenario-row header">
            <span>Scenario</span>
            <span>Share of $1T GMV</span>
            <span>GMV</span>
            <span>Take rate</span>
            <span>Annual revenue</span>
          </div>
          {revenueScenarios.map((scenario) => (
            <div className="pitch-scenario-row" key={scenario.name}>
              <span>{scenario.name}</span>
              <span>{scenario.share}</span>
              <span>{scenario.gmv}</span>
              <span>{scenario.takeRate}</span>
              <span>{scenario.revenue}</span>
            </div>
          ))}
        </div>
        <p className="pitch-assumption">
          Scenario math is illustrative: GMV share and 3% take rate are assumptions, while market
          anchor and commission model are sourced above.
        </p>
      </section>

      <section className="pitch-slide pitch-team" id="team" aria-labelledby="team-title">
        <div className="pitch-section-head">
          <p className="pitch-kicker">06 / Team</p>
          <h2 id="team-title">Founder-led, protocol-native, built close to the transaction.</h2>
          <p>
            The company is being built where the hard parts are: buyer memory, merchant capability
            discovery, checkout, consent, and post-purchase state.
          </p>
        </div>
        <div className="pitch-team-grid">
          <article>
            <span>Founder</span>
            <h3>David Tilser</h3>
            <p>Product, engineering, and agentic commerce architecture.</p>
            <SourceLink source={SOURCES.meantUcpSpike} />
          </article>
          <article>
            <span>Built</span>
            <h3>Full-stack personal commerce app</h3>
            <p>Search, preferences, merchant registry, product ranking, cart, checkout, and orders.</p>
            <SourceLink source={SOURCES.meantReadme} />
          </article>
          <article>
            <span>Next</span>
            <h3>Design partners and integrations</h3>
            <p>Merchant onboarding, UCP checkout, payment handlers, and external agent APIs.</p>
            <SourceLink source={SOURCES.meantUcpSpike} />
          </article>
        </div>
      </section>

      <section className="pitch-sources" id="sources" aria-labelledby="sources-title">
        <p className="pitch-kicker">Appendix</p>
        <h2 id="sources-title">Sources</h2>
        <div className="pitch-source-groups">
          {sourceGroups.map((group) => (
            <section className="pitch-source-group" key={group.title}>
              <h3>{group.title}</h3>
              <div>
                {group.sources.map((source) => (
                  <SourceLink key={source.href} source={source} />
                ))}
              </div>
            </section>
          ))}
        </div>
      </section>
    </main>
  )
}

function SourceLink({ source }: Readonly<{ source: PitchSource }>) {
  return (
    <a className="pitch-source-link" href={source.href} target="_blank" rel="noreferrer">
      {source.label}
    </a>
  )
}
