import type { Product } from '../types'

export interface Message {
  role: 'you' | 'ai'
  text: string
  products?: readonly Product[]
  pending?: boolean
}

export interface AskReplyDraft {
  id: string
  label: string
  text: string
  suggestedText: string
}
