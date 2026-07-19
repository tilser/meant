export type AskComposerSubmissionResult = boolean | void
export type AskComposerSubmitHandler = (
  question: string,
) => AskComposerSubmissionResult | Promise<AskComposerSubmissionResult>

export function askComposerSuggestionValue(
  label: string,
  index: number,
  values: readonly string[],
): string {
  return values[index]?.trim() || label
}

/** A rejected submission keeps the user's draft available for another attempt. */
export async function askComposerSubmissionAccepted(
  onAsk: AskComposerSubmitHandler,
  question: string,
): Promise<boolean> {
  try {
    return (await onAsk(question)) !== false
  } catch {
    return false
  }
}
