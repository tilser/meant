import { sanitizeBuyerVisibleText } from './buyerVisibleText'

const NEUTRALIZED_MARKDOWN_LINK = /(?<!!)\[([^\n]+?)\]\([^\n)]*\bthe merchant\b[^\n)]*\)/giu

/** Preserves safe Markdown while removing links whose transport-only destinations were neutralized. */
export function agentMarkdownText(value: string): string {
  return sanitizeBuyerVisibleText(value).replace(NEUTRALIZED_MARKDOWN_LINK, '$1').trim()
}

/** Reduces agent prose to plain text for previews, Shelf snapshots, and other text-only UI. */
export function plainAgentText(value: string): string {
  return agentMarkdownText(value)
    .replace(/!\[([^\]]*)]\([^)]+\)/g, '$1')
    .replace(/\[([^\]]+)]\([^)]+\)/g, '$1')
    .replace(/\[([^\]]+)]\[[^\]]*]/g, '$1')
    .replace(/(^|\n)\s{0,3}#{1,6}\s+/g, '$1')
    .replace(/\*\*([^*\n]+)\*\*/g, '$1')
    .replace(/__([^_\n]+)__/g, '$1')
    .replace(/`([^`\n]+)`/g, '$1')
    .replace(/(^|\n)\s{0,3}>\s?/g, '$1')
    .replace(/(^|\s)[*+-]\s+(?=\S)/g, '$1')
    .replace(/\*([^*\n]+)\*/g, '$1')
    .trim()
}
