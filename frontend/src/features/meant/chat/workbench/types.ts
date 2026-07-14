export interface WorkbenchInsight {
  id: string
  label: string
  text: string
  suggestedReply: string
  fresh?: boolean
}

export interface WorkbenchAgent {
  id: string
  task: string
  status: 'working' | 'done'
  result: string
  openedInChat?: boolean
}
