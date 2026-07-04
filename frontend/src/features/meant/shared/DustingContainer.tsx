import { type ReactNode, useEffect, useRef } from 'react'

let dustSequence = 0

export function DustingContainer({
  children,
  dusting,
  className,
  onGone,
}: Readonly<{
  children: ReactNode
  dusting: boolean
  className?: string
  onGone: () => void
}>) {
  const idRef = useRef<string>('')
  const displacementRef = useRef<SVGFEDisplacementMapElement | null>(null)
  const blurRef = useRef<SVGFEGaussianBlurElement | null>(null)

  if (!idRef.current) {
    dustSequence += 1
    idRef.current = `mtdust-solo-${dustSequence}`
  }

  useEffect(() => {
    if (!dusting) {
      return undefined
    }
    let frame = 0
    let startedAt = 0
    const duration = 1050
    const step = (time: number) => {
      if (!startedAt) {
        startedAt = time
      }
      const progress = Math.min(1, (time - startedAt) / duration)
      const eased = progress * progress
      displacementRef.current?.setAttribute('scale', (eased * 140).toFixed(1))
      blurRef.current?.setAttribute('stdDeviation', (eased * 1.8).toFixed(2))
      if (progress < 1) {
        frame = window.requestAnimationFrame(step)
        return
      }
      window.setTimeout(onGone, 20)
    }
    frame = window.requestAnimationFrame(step)
    return () => window.cancelAnimationFrame(frame)
  }, [dusting, onGone])

  return (
    <div
      className={`${className ?? ''} ${dusting ? 'mt-dusting-solo' : ''}`.trim() || undefined}
      style={dusting ? { filter: `url(#${idRef.current})` } : undefined}
    >
      {children}
      {dusting ? (
        <svg className="mt-dust-svg" aria-hidden="true" width="0" height="0">
          <defs>
            <filter
              id={idRef.current}
              x="-40%"
              y="-40%"
              width="180%"
              height="180%"
              colorInterpolationFilters="sRGB"
            >
              <feTurbulence
                type="fractalNoise"
                baseFrequency="0.7"
                numOctaves="2"
                seed={dustSequence}
                result="n"
              />
              <feDisplacementMap
                ref={displacementRef}
                in="SourceGraphic"
                in2="n"
                scale="0"
                xChannelSelector="R"
                yChannelSelector="G"
                result="d"
              />
              <feGaussianBlur ref={blurRef} in="d" stdDeviation="0" />
            </filter>
          </defs>
        </svg>
      ) : null}
    </div>
  )
}
