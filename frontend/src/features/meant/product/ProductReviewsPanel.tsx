import { useEffect, useState } from 'react'

import {
  getProductReviews,
  type ProductReviewProfile,
  type ProductReviewsProfile,
} from '../../../lib/apiClient'
import type { Product } from '../types'

type ProductReviewsPanelMode = 'modal' | 'chat'
type ReviewLoadState = 'idle' | 'loading' | 'loaded' | 'error'

const REVIEW_PAGE_SIZE = 5

function reviewStars(rating: number | null | undefined): string {
  if (rating === null || rating === undefined) {
    return ''
  }
  return '★'.repeat(Math.max(0, Math.min(5, Math.round(rating))))
}

function fallbackReviewInsight(product: Product): string {
  if (product.review.count <= 0) {
    return 'No review data available from this catalog result.'
  }
  return product.review.insight || 'Rating data is available; no review-summary agent has run yet.'
}

function formatReviewDate(value: string | null): string | null {
  if (!value) {
    return null
  }
  const date = new Date(value)
  if (Number.isNaN(date.getTime())) {
    return null
  }
  return new Intl.DateTimeFormat(undefined, {
    month: 'short',
    day: 'numeric',
    year: 'numeric',
  }).format(date)
}

function reviewIdentity(review: ProductReviewProfile): string {
  return [review.externalId, review.author, review.createdAt, review.content?.slice(0, 80)]
    .filter(Boolean)
    .join('|')
}

function mergeReviewPages(
  current: readonly ProductReviewProfile[],
  next: readonly ProductReviewProfile[],
): ProductReviewProfile[] {
  const seen = new Set(current.map(reviewIdentity).filter(Boolean))
  const merged = [...current]
  next.forEach((review) => {
    const key = reviewIdentity(review)
    if (key && seen.has(key)) {
      return
    }
    if (key) {
      seen.add(key)
    }
    merged.push(review)
  })
  return merged
}

function reviewStatusText(
  product: Product,
  response: ProductReviewsProfile | null,
  loadState: ReviewLoadState,
  reviews: readonly ProductReviewProfile[],
): string {
  if (loadState === 'loading' && !response) {
    return 'Fetching latest merchant reviews...'
  }
  if (loadState === 'error' && !response) {
    return 'Latest merchant reviews are unavailable right now.'
  }
  if (response?.supported === false) {
    return response.message || fallbackReviewInsight(product)
  }
  if (reviews.length > 0) {
    return 'Latest merchant reviews for this product.'
  }
  if (response?.supported && response.reviewCount > 0) {
    return 'Review totals are available, but no written reviews were returned.'
  }
  return fallbackReviewInsight(product)
}

function ReviewRow({ review }: Readonly<{ review: ProductReviewProfile }>) {
  const date = formatReviewDate(review.createdAt)
  const stars = reviewStars(review.rating)
  const hasMeta = Boolean(review.verified || date || review.variantTitle)

  return (
    <article className="mt-review-row">
      <div className="mt-review-row-head">
        <span className="mt-review-author">{review.author || 'Customer review'}</span>
        {review.rating !== null ? (
          <span className="mt-review-rating">
            {stars ? <span className="mt-stars">{stars}</span> : null}
            <span className="mt-mono">{review.rating}/5</span>
          </span>
        ) : null}
      </div>
      {review.content ? <p className="mt-review-content">{review.content}</p> : null}
      {hasMeta ? (
        <div className="mt-review-meta mt-mono">
          {review.verified ? <span>Verified</span> : null}
          {date ? <span>{date}</span> : null}
          {review.variantTitle ? <span>{review.variantTitle}</span> : null}
        </div>
      ) : null}
    </article>
  )
}

export function ProductReviewsPanel({
  product,
  userId,
  mode = 'modal',
}: Readonly<{
  product: Product
  userId?: string
  mode?: ProductReviewsPanelMode
}>) {
  const [response, setResponse] = useState<ProductReviewsProfile | null>(null)
  const [reviews, setReviews] = useState<ProductReviewProfile[]>([])
  const [loadState, setLoadState] = useState<ReviewLoadState>('idle')
  const [loadMorePending, setLoadMorePending] = useState(false)
  const [nextOffset, setNextOffset] = useState(0)
  const [pageError, setPageError] = useState<string | null>(null)
  const merchantId = product.merchantId ?? null
  const productId = product.merchantProductId ?? null
  const canFetchReviews = Boolean(product.remote && merchantId && productId)

  useEffect(() => {
    setResponse(null)
    setReviews([])
    setNextOffset(0)
    setPageError(null)

    if (!canFetchReviews || !merchantId || !productId) {
      setLoadState('idle')
      return
    }

    const controller = new AbortController()
    setLoadState('loading')
    getProductReviews({
      merchantId,
      productId,
      limit: REVIEW_PAGE_SIZE,
      offset: 0,
      signal: controller.signal,
      expectedUserId: userId,
    })
      .then((nextResponse) => {
        if (controller.signal.aborted) {
          return
        }
        setResponse(nextResponse)
        setReviews(nextResponse.reviews ?? [])
        setNextOffset(REVIEW_PAGE_SIZE)
        setLoadState('loaded')
      })
      .catch(() => {
        if (controller.signal.aborted) {
          return
        }
        setLoadState('error')
      })

    return () => controller.abort()
  }, [canFetchReviews, merchantId, product.id, productId, userId])

  const loadMoreReviews = async () => {
    if (!merchantId || !productId || loadMorePending) {
      return
    }
    setLoadMorePending(true)
    setPageError(null)
    try {
      const nextResponse = await getProductReviews({
        merchantId,
        productId,
        limit: REVIEW_PAGE_SIZE,
        offset: nextOffset,
        expectedUserId: userId,
      })
      setResponse(nextResponse)
      setReviews((current) => mergeReviewPages(current, nextResponse.reviews ?? []))
      setNextOffset((current) => current + REVIEW_PAGE_SIZE)
      setLoadState('loaded')
    } catch {
      setPageError('Could not load more reviews right now.')
    } finally {
      setLoadMorePending(false)
    }
  }

  const useResponseAggregate = response?.supported !== false
  const score = useResponseAggregate
    ? (response?.rating ?? product.review.score)
    : product.review.score
  const reviewCount = useResponseAggregate
    ? (response?.reviewCount ?? product.review.count)
    : product.review.count
  const statusText = reviewStatusText(product, response, loadState, reviews)
  const showMore = Boolean(response?.supported && response.hasMore)
  const Root = mode === 'modal' ? 'section' : 'div'

  return (
    <Root className={mode === 'modal' ? 'mt-block' : 'mt-ct-block'}>
      <div className={mode === 'modal' ? 'mt-reviews-head' : 'mt-ct-block-head'}>
        <div className={mode === 'modal' ? 'mt-block-label mt-mono' : 'mt-mono mt-ct-block-key'}>
          {mode === 'modal' ? 'From the reviews' : `Reviews · ${product.name}`}
        </div>
        {reviewCount > 0 ? (
          <div className="mt-reviews-score">
            {score !== null ? <span className="mt-stars">{reviewStars(score)}</span> : null}
            <span className="mt-mono">
              {score !== null ? `${score.toFixed(1)} · ` : ''}
              {reviewCount.toLocaleString()}
              {score === null ? ' reviews' : ''}
            </span>
          </div>
        ) : (
          <span className="mt-mono mt-reviews-empty">No review data</span>
        )}
      </div>
      <p className={mode === 'modal' ? 'mt-reviews-insight' : 'mt-ct-review-sum'}>{statusText}</p>
      {reviews.length > 0 ? (
        <div className="mt-review-list">
          {reviews.map((review, index) => (
            <ReviewRow key={`${reviewIdentity(review)}-${index}`} review={review} />
          ))}
        </div>
      ) : null}
      {pageError ? <p className="mt-review-page-error mt-mono">{pageError}</p> : null}
      {showMore ? (
        <button
          className="mt-review-more"
          type="button"
          disabled={loadMorePending}
          onClick={() => void loadMoreReviews()}
        >
          {loadMorePending ? 'Loading reviews...' : 'Load more reviews'}
        </button>
      ) : null}
    </Root>
  )
}
