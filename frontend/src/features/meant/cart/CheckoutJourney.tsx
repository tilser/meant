import { MeantHeartMark, SparkMark } from '../shared/ui'
import type { ActiveCheckoutSession } from './checkoutTypes'

export type CheckoutJourneyStage = 'delivery' | 'secure'

function checkoutProductTitle(session: ActiveCheckoutSession): string {
  const title = session.items[0]?.productTitle?.trim()
  return title || (session.items.length === 1 ? 'Your find' : 'Your finds')
}

export function CheckoutJourney({
  session,
  stage,
  merchantDisplay,
  titleId,
  compact = false,
}: Readonly<{
  session: ActiveCheckoutSession
  stage: CheckoutJourneyStage
  merchantDisplay: string
  titleId?: string
  compact?: boolean
}>) {
  const firstItem = session.items[0]
  const title = checkoutProductTitle(session)
  const extraItems = Math.max(0, session.items.length - 1)
  const deliveryComplete = stage === 'secure'

  return (
    <div
      className={`mt-checkout-journey${compact ? ' compact' : ''} stage-${stage}`}
      data-stage={stage}
    >
      <div className="mt-checkout-journey-copy">
        <div className="mt-mono mt-checkout-journey-eyebrow">
          {stage === 'delivery' ? 'Meant together' : 'Almost Meant to be'}
        </div>
        <h2 id={titleId}>
          {stage === 'delivery' ? (
            <>
              We knew you two were <em>Meant</em> together.
            </>
          ) : (
            <>
              What’s <em>Meant</em> for you is close.
            </>
          )}
        </h2>
        <p>
          {stage === 'delivery'
            ? 'Now, where should we send the find that feels this right?'
            : `Your details are set. One secure step with ${merchantDisplay}, and this match can be yours.`}
        </p>
      </div>

      <div className="mt-checkout-match" aria-label={`You and ${title}, matched by Meant`}>
        <span className="mt-checkout-match-person">You</span>
        <span className="mt-checkout-match-line" aria-hidden />
        <span className="mt-checkout-match-heart" aria-hidden>
          <span />
          <MeantHeartMark size={compact ? 25 : 32} />
        </span>
        <span className="mt-checkout-match-line" aria-hidden />
        <span className="mt-checkout-match-product">
          {firstItem?.imageUrl ? (
            <img src={firstItem.imageUrl} alt="" />
          ) : (
            <SparkMark size={compact ? 16 : 19} />
          )}
          {extraItems > 0 ? <small>+{extraItems}</small> : null}
        </span>
      </div>

      <div className="mt-checkout-steps" aria-label="Checkout progress">
        <div className="mt-checkout-step matched done">
          <span aria-hidden>✓</span>
          <strong>Matched</strong>
        </div>
        <i aria-hidden />
        <div
          className={`mt-checkout-step delivery ${deliveryComplete ? 'done' : 'active'}`}
          aria-current={!deliveryComplete ? 'step' : undefined}
        >
          <span aria-hidden>{deliveryComplete ? '✓' : '2'}</span>
          <strong>Delivery</strong>
        </div>
        <i aria-hidden />
        <div
          className={`mt-checkout-step secure${deliveryComplete ? ' active' : ''}`}
          aria-current={deliveryComplete ? 'step' : undefined}
        >
          <span aria-hidden>3</span>
          <strong>Secure checkout</strong>
        </div>
      </div>
    </div>
  )
}
