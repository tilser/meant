export interface AutomaticCampaignBriefAttempt {
  current: string | null
}

export async function submitAutomaticCampaignBrief({
  initialBrief,
  unavailable,
  attempt,
  submit,
  onConsumed,
}: Readonly<{
  initialBrief: string
  unavailable: boolean
  attempt: AutomaticCampaignBriefAttempt
  submit: (brief: string) => Promise<boolean>
  onConsumed: () => void
}>): Promise<boolean> {
  const brief = initialBrief.trim()
  if (!brief || unavailable || attempt.current === brief) return false

  attempt.current = brief
  try {
    const accepted = await submit(brief)
    if (!accepted) {
      attempt.current = null
      return false
    }
    onConsumed()
    return true
  } catch (cause) {
    attempt.current = null
    throw cause
  }
}
