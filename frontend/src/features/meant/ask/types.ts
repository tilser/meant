import type { Product } from '../types'

export interface AskPanelSize {
  width: number
  height: number
}

export interface Message {
  role: 'you' | 'ai'
  text: string
  products?: readonly Product[]
  pending?: boolean
}
