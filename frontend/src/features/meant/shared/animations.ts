export function flyToShelf(fromElement: HTMLElement | null, tone?: string | null) {
  if (!fromElement || typeof document === 'undefined') {
    return
  }
  const shelf = document.querySelector('.mt-shelf.open') ?? document.querySelector('.mt-shelf-tab')
  if (!(shelf instanceof HTMLElement)) {
    return
  }
  const from = fromElement.getBoundingClientRect()
  const to = shelf.getBoundingClientRect()
  const ghost = document.createElement('div')
  ghost.className = 'mt-fly-ghost'
  ghost.style.left = `${from.left + from.width / 2 - 15}px`
  ghost.style.top = `${from.top + from.height / 2 - 15}px`
  ghost.style.background = tone || 'var(--accent)'
  document.body.appendChild(ghost)
  const tx = to.left + to.width / 2 - (from.left + from.width / 2)
  const ty = to.top + to.height / 2 - (from.top + from.height / 2)
  const animation = ghost.animate(
    [
      { transform: 'translate(0, 0) scale(1)', opacity: 1, borderRadius: '9px' },
      {
        transform: `translate(${tx * 0.5}px, ${ty * 0.5 - 46}px) scale(.78)`,
        opacity: 1,
        borderRadius: '11px',
        offset: 0.6,
      },
      { transform: `translate(${tx}px, ${ty}px) scale(.2)`, opacity: 0, borderRadius: '50%' },
    ],
    { duration: 640, easing: 'cubic-bezier(.5,0,.2,1)' },
  )
  animation.onfinish = () => ghost.remove()
}

export function flyMessageToChat(fromElement: HTMLElement | null, text: string) {
  if (!fromElement || typeof document === 'undefined') {
    return
  }
  const from = fromElement.getBoundingClientRect()
  const ghost = document.createElement('div')
  ghost.className = 'mt-fly-msg'
  ghost.textContent = text.length > 64 ? `${text.slice(0, 62)}...` : text
  ghost.style.left = `${from.left}px`
  ghost.style.top = `${from.top}px`
  ghost.style.maxWidth = `${Math.min(from.width, 360)}px`
  document.body.appendChild(ghost)
  const tx = window.innerWidth / 2 - (from.left + from.width / 2)
  const ty = window.innerHeight - 118 - from.top
  const animation = ghost.animate(
    [
      { transform: 'translate(0, 0) scale(1)', opacity: 0.96 },
      {
        transform: `translate(${tx * 0.35}px, ${ty * 0.55}px) scale(.94)`,
        opacity: 1,
        offset: 0.5,
      },
      { transform: `translate(${tx}px, ${ty}px) scale(.7)`, opacity: 0 },
    ],
    { duration: 680, easing: 'cubic-bezier(.5,0,.2,1)' },
  )
  animation.onfinish = () => ghost.remove()
}
