export function ComingSoonNewsletter({
  newsletter,
  newsletterPending,
  onNewsletterSignup,
}: Readonly<{
  newsletter: boolean
  newsletterPending: boolean
  onNewsletterSignup: () => void
}>) {
  return (
    <div className="mt-ct-newsletter">
      <p>This functionality isn't ready yet. We're working on it!</p>
      {newsletter ? (
        <p>
          <em>
            You're already subscribed to the newsletter, so you'll hear about it as soon as it's
            ready.
          </em>
        </p>
      ) : (
        <p>
          <em>
            If you want to be the first to know about new features,{' '}
            <button
              className="mt-ct-inline-link"
              type="button"
              disabled={newsletterPending}
              onClick={onNewsletterSignup}
            >
              sign up
            </button>{' '}
            for our newsletter.
          </em>
        </p>
      )}
    </div>
  )
}
