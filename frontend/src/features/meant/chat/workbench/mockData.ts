import type { Preference, Product } from '../../types'
import { money } from '../../utils'
import type { WorkbenchAgent, WorkbenchInsight } from './types'

export const MOCK_WORKBENCH_INSIGHTS: readonly WorkbenchInsight[] = [
  {
    id: 'mock-profile-filter',
    label: 'Profile signal',
    text: 'Natural materials and strong reviews are doing the most work in your current recommendations. I am keeping synthetic-heavy options out of the way.',
    suggestedReply: 'Show me how my natural-material preference changes these results.',
  },
  {
    id: 'mock-price-pattern',
    label: 'Worth comparing',
    text: 'The strongest value usually sits in the middle of this set. The top-priced option adds polish, but not a meaningfully better match for you.',
    suggestedReply: 'Compare the best-value option with the top-rated one.',
  },
]

export const MOCK_WORKBENCH_AGENTS: readonly WorkbenchAgent[] = [
  {
    id: 'mock-agent-materials',
    task: 'Check which natural-material picks are genuinely durable',
    status: 'done',
    result:
      'I checked material composition, care notes, and recurring review themes. Two picks stand out: the mid-priced option has the best durability record, while the premium one mainly wins on finish. I would shortlist the mid-priced pick first.',
  },
  {
    id: 'mock-agent-prices',
    task: 'Look for a better price on the strongest match',
    status: 'working',
    result: '',
  },
]

const MOCK_RESULT_BODIES = [
  'Three things stood out: the best value sits in the middle of the range, the top-rated option is not the most expensive, and one popular pick quietly misses an important preference. I can shortlist the two winners in this chat.',
  'Quality levels off faster than price does. Two brands own the strongest buyer feedback, and the more affordable one also ships sooner. I would compare those two before considering the premium pick.',
  'Most of the premium positioning is not useful for your profile. Two options clear your requirements comfortably, and both have consistent recent reviews. I can pull the stronger one into the conversation.',
] as const

function stableResultIndex(task: string): number {
  return (
    Array.from(task).reduce((total, character) => total + character.charCodeAt(0), 0) %
    MOCK_RESULT_BODIES.length
  )
}

export function mockWorkbenchAgentResult(task: string): string {
  const cleanTask = task.trim().replace(/[.\s]+$/, '')
  return `Done researching “${cleanTask}”. I checked current mock prices, product details, and buyer feedback. ${MOCK_RESULT_BODIES[stableResultIndex(cleanTask)]}`
}

function preferenceLabels(
  preferenceIds: readonly string[],
  preferences: readonly Preference[],
): string[] {
  const labelsById = new Map(preferences.map((preference) => [preference.id, preference.label]))
  return preferenceIds.map((id) => labelsById.get(id) ?? id)
}

export function buildMockWorkbenchInsights(
  product: Product | null,
  query: string,
  preferences: readonly Preference[],
): WorkbenchInsight[] {
  const insights: WorkbenchInsight[] = []
  const normalizedQuery = query.trim()
  if (normalizedQuery) {
    insights.push({
      id: `query:${normalizedQuery.toLowerCase().slice(0, 72)}`,
      label: 'You searched',
      text: `You looked for “${normalizedQuery}”. I filtered the available catalog against your profile before ranking what appears in chat.`,
      suggestedReply: `Tell me what mattered most in my search for “${normalizedQuery}”.`,
    })
  }

  if (!product) {
    return insights
  }

  insights.push({
    id: `${product.id}:focus`,
    label: "You're looking at",
    text: `${product.name} · ${product.brand}. ${product.note}`,
    suggestedReply: `Tell me more about the ${product.name}.`,
  })

  if (product.review.score !== null && product.review.count > 0) {
    insights.push({
      id: `${product.id}:reviews`,
      label: 'Review signal',
      text: `${product.review.score.toFixed(1)}★ from ${product.review.count.toLocaleString()} buyers. ${product.review.insight}`,
      suggestedReply: `What do reviewers say about the ${product.name}?`,
    })
  }

  const matches = preferenceLabels(product.satisfies, preferences)
  if (matches.length > 0) {
    insights.push({
      id: `${product.id}:match`,
      label: `${product.match}% match to you`,
      text: `It matches ${matches.slice(0, 3).join(', ')}${matches.length > 3 ? ' and more' : ''}. That is why it ranks highly for you.`,
      suggestedReply: `Explain why the ${product.name} matches me.`,
    })
  }

  const misses = preferenceLabels(product.misses, preferences)
  if (misses.length > 0) {
    insights.push({
      id: `${product.id}:catch`,
      label: 'Heads up',
      text: `It does not meet ${misses.join(', ')}. That is worth weighing before you buy.`,
      suggestedReply: `How important is the catch with the ${product.name}?`,
    })
  } else if (product.cons[0]) {
    insights.push({
      id: `${product.id}:catch`,
      label: 'One catch',
      text: `${product.cons[0]}. It may not be a dealbreaker, but it is worth knowing.`,
      suggestedReply: `How important is the catch with the ${product.name}?`,
    })
  }

  insights.push({
    id: `${product.id}:price`,
    label: 'Best price',
    text:
      product.priceFrom === null
        ? `Current pricing is unavailable. I would verify the live offer before making a decision.`
        : `From ${money(product.priceFrom, product.priceCurrency)} across ${product.merchants} store${product.merchants === 1 ? '' : 's'}. The cheapest available offer stays on top.`,
    suggestedReply: `Find the best current offer for the ${product.name}.`,
  })

  return insights
}
