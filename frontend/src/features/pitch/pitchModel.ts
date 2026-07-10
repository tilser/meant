export type PitchSource = {
  readonly id: string
  readonly label: string
  readonly title: string
  readonly href: string
  readonly note: string
}

export const pitchSources = {
  mckinseyArticle: {
    id: 'mckinsey-article',
    label: 'McKinsey · 2030 range',
    title: 'The automation curve in agentic commerce',
    href: 'https://www.mckinsey.com/capabilities/quantumblack/our-insights/the-automation-curve-in-agentic-commerce',
    note: 'Moderate scenarios: $3T–$5T of global consumer commerce mediated by agents in 2030.',
  },
  mckinseyReport: {
    id: 'mckinsey-report',
    label: 'McKinsey · direct report',
    title: 'The agentic commerce opportunity',
    href: 'https://www.mckinsey.com/~/media/mckinsey/business%20functions/quantumblack/our%20insights/the%20agentic%20commerce%20opportunity%20how%20ai%20agents%20are%20ushering%20in%20a%20new%20era%20for%20consumers%20and%20merchants/the-agentic-commerce-opportunity-how-ai-agents-are-ushering-in-a-new-era-for-consumers-and-merchants_final.pdf',
    note: '$900B–$1T of US B2C goods and $3T–$5T globally; the figures exclude services and significant B2B commerce.',
  },
  adobeHoliday: {
    id: 'adobe-holiday',
    label: 'Adobe · 2025 holiday data',
    title: 'Adobe holiday shopping season analysis',
    href: 'https://news.adobe.com/news/2026/01/adobe-holiday-shopping-season',
    note: 'Generative-AI referral traffic to US retail sites rose 693.4% YoY from a base Adobe says remains modest.',
  },
  googleUcp: {
    id: 'google-ucp',
    label: 'Google · UCP',
    title: 'Under the Hood: Universal Commerce Protocol',
    href: 'https://developers.googleblog.com/en/under-the-hood-universal-commerce-protocol-ucp/',
    note: 'Open commerce language developed with Shopify, Etsy, Wayfair, Target, and Walmart and endorsed by 20+ partners.',
  },
  googleAp2: {
    id: 'google-ap2',
    label: 'Google · AP2',
    title: 'Announcing Agent Payments Protocol',
    href: 'https://cloud.google.com/blog/products/ai-machine-learning/announcing-agents-to-payments-ap2-protocol',
    note: 'More than 60 organizations; cryptographically signed intent and cart mandates for authorization and accountability.',
  },
  shopifyAgents: {
    id: 'shopify-agents',
    label: 'Shopify · agent rails',
    title: 'Build commerce agents with UCP',
    href: 'https://shopify.dev/docs/agents',
    note: 'UCP-aligned discovery, cart, checkout, direct completion for trusted agents, and order lifecycle tooling.',
  },
  shopifyCatalog: {
    id: 'shopify-catalog',
    label: 'Shopify · Global Catalog',
    title: 'About Catalogs',
    href: 'https://shopify.dev/docs/agents/catalog',
    note: 'Cross-merchant Global Catalog and merchant-scoped Storefront Catalog interfaces.',
  },
  shopifyCheckout: {
    id: 'shopify-checkout',
    label: 'Shopify · cart + checkout',
    title: 'Carts and checkout for agents',
    href: 'https://shopify.dev/docs/agents/carts-and-checkout',
    note: 'Cart iteration, checkout creation, merchant handoff, and capability-gated completion.',
  },
  shopifyCheckoutKit: {
    id: 'shopify-checkout-kit',
    label: 'Shopify · Checkout Kit / ECP',
    title: 'Checkout Kit',
    href: 'https://shopify.dev/docs/agents/carts-and-checkout/checkout-kit',
    note: 'An SDK for embedded Shopify checkout, built on the Embedded Checkout Protocol.',
  },
  visaTrustedAgent: {
    id: 'visa-trusted-agent',
    label: 'Visa · Trusted Agent Protocol',
    title: 'Visa Unveils Trusted Agent Protocol for AI Commerce',
    href: 'https://corporate.visa.com/en/sites/visa-perspectives/newsroom/visa-unveils-trusted-agent-protocol-for-ai-commerce.html',
    note: 'Cryptographic agent identity and intent signals help merchants distinguish approved agents from malicious automation.',
  },
  mastercardAgentPay: {
    id: 'mastercard-agent-pay',
    label: 'Mastercard · Agent Pay',
    title: 'Mastercard unveils Agent Pay',
    href: 'https://newsroom.mastercard.com/news/press/2025/april/mastercard-unveils-agent-pay-pioneering-agentic-payments-technology-to-power-commerce-in-the-age-of-ai/',
    note: 'Agent registration, tokenized payments, transaction recognition, and consumer controls.',
  },
  amazonBuyForMe: {
    id: 'amazon-buy-for-me',
    label: 'Amazon · Buy for Me',
    title: "Amazon's Buy for Me feature",
    href: 'https://www.aboutamazon.com/news/retail/amazon-shopping-app-buy-for-me-brands',
    note: 'A beta experience that can buy selected items from brand sites inside the Amazon Shopping app.',
  },
  openAiDiscovery: {
    id: 'openai-discovery',
    label: 'OpenAI · product discovery',
    title: 'Powering Product Discovery in ChatGPT',
    href: 'https://openai.com/index/powering-product-discovery-in-chatgpt/',
    note: 'Shopify Catalog powers discovery while merchants retain their own checkout experiences.',
  },
  meantReadme: {
    id: 'meant-readme',
    label: 'Meant · product vision',
    title: 'Meant README',
    href: 'https://github.com/davidtilser/meant/blob/main/README.md',
    note: 'Personal Commerce OS vision, layers, compatibility principles, experience, and business model.',
  },
  meantPlan: {
    id: 'meant-plan',
    label: 'Meant · architecture plan',
    title: 'Personal Commerce OS implementation plan',
    href: 'https://github.com/davidtilser/meant/blob/main/plans/personal-commerce-os-implementation-plan.md',
    note: 'Provider-neutral product, offer, ranking, merchant integration, and checkout architecture.',
  },
} as const satisfies Record<string, PitchSource>

export const pitchSourceGroups = [
  {
    title: 'Market shift',
    sources: [pitchSources.mckinseyArticle, pitchSources.mckinseyReport, pitchSources.adobeHoliday],
  },
  {
    title: 'Surfaces, standards, and rails',
    sources: [
      pitchSources.amazonBuyForMe,
      pitchSources.openAiDiscovery,
      pitchSources.googleUcp,
      pitchSources.shopifyAgents,
      pitchSources.shopifyCatalog,
      pitchSources.shopifyCheckout,
      pitchSources.shopifyCheckoutKit,
      pitchSources.googleAp2,
      pitchSources.visaTrustedAgent,
      pitchSources.mastercardAgentPay,
    ],
  },
  {
    title: 'Meant',
    sources: [pitchSources.meantReadme, pitchSources.meantPlan],
  },
] as const

export const MARKET_ANCHOR_GMV = 4_000_000_000_000
export const DEFAULT_SHARE_PERCENT = 0.1
export const DEFAULT_TAKE_RATE_PERCENT = 2
export const MODEL_ASSUMPTION_LABEL = 'Meant model assumption · illustrative, not a forecast'

export type RevenueInputs = {
  readonly marketGmv: number
  readonly sharePercent: number
  readonly takeRatePercent: number
}

export type RevenueResult = RevenueInputs & {
  readonly gmv: number
  readonly revenue: number
}

export function calculateRevenueScenario({
  marketGmv,
  sharePercent,
  takeRatePercent,
}: RevenueInputs): RevenueResult {
  const inputs = [marketGmv, sharePercent, takeRatePercent]
  if (inputs.some((value) => !Number.isFinite(value) || value < 0)) {
    throw new RangeError('Revenue scenario inputs must be finite and non-negative')
  }

  const gmv = marketGmv * (sharePercent / 100)
  return {
    marketGmv,
    sharePercent,
    takeRatePercent,
    gmv,
    revenue: gmv * (takeRatePercent / 100),
  }
}

export function formatCompactUsd(value: number): string {
  return new Intl.NumberFormat('en-US', {
    compactDisplay: 'short',
    currency: 'USD',
    currencyDisplay: 'narrowSymbol',
    maximumFractionDigits: 1,
    notation: 'compact',
    style: 'currency',
  }).format(value)
}

export const revenueScenarioPresets = [
  { id: 'focused', label: 'Focused', sharePercent: 0.02 },
  { id: 'base', label: 'Illustrative', sharePercent: 0.1 },
  { id: 'scale', label: 'Scale', sharePercent: 0.5 },
] as const
