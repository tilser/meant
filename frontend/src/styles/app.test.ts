import { describe, expect, test } from 'bun:test'
import { readFileSync } from 'node:fs'

const styles = readFileSync(new URL('./app.css', import.meta.url), 'utf8')

describe('theme and upload styles', () => {
  test('applies the high-contrast logo treatment only in dark mode', () => {
    const lightLogoStyles = styles.match(/\n\.mt-brand-logo\s*\{([^}]*)\}/)?.[1]

    expect(styles).toMatch(
      /\[data-theme='dark'\] \.mt-brand-logo\s*\{[^}]*filter: brightness\(0\) invert\(1\);[^}]*\}/,
    )
    expect(lightLogoStyles).not.toContain('filter:')
  })

  test('keeps the file input focusable while styling its visible label', () => {
    const fileInputStyles = styles.match(/\n\.mt-file-input\s*\{([^}]*)\}/)?.[1]

    expect(styles).toContain('.mt-file-input:focus-visible + .mt-file-trigger')
    expect(fileInputStyles).not.toContain('display: none')
  })
})

describe('discover chat layout styles', () => {
  test('pins conversation controls directly below the desktop navigation', () => {
    expect(styles).toMatch(/\.mt-ct-tabs\s*\{[^}]*top: 64px;[^}]*\}/)
    expect(styles).toMatch(
      /@media \(max-width: 760px\)\s*\{[\s\S]*?\.mt-ct-tabs\s*\{[^}]*top: 86px;[^}]*\}/,
    )
  })

  test('does not leave feed padding below the sticky composer', () => {
    expect(styles).toMatch(/\.mt-feed\.mt-ct-feed\s*\{[^}]*padding-bottom: 0;[^}]*\}/)
  })

  test('turns grounded agent product mentions into responsive visual previews', () => {
    expect(styles).toMatch(
      /\.mt-agent-markdown \.mt-agent-product-link\s*\{[^}]*display: inline-grid;[^}]*grid-template-columns: 52px minmax\(0, 1fr\);[^}]*\}/,
    )
    expect(styles).toMatch(/\.mt-agent-product-link-image\s*\{[^}]*object-fit: cover;[^}]*\}/)
    expect(styles).toMatch(
      /@media \(max-width: 720px\)\s*\{[\s\S]*?\.mt-agent-markdown \.mt-agent-product-link\s*\{[^}]*width: 100%;[^}]*max-width: 100%;[^}]*\}/,
    )
  })
})

describe('compare verdict styles', () => {
  test('keeps the Meant pick compact when product copy is long', () => {
    expect(styles).toMatch(
      /\.mt-cmp-verdict\s*\{[^}]*grid-template-columns: auto minmax\(220px, 0\.9fr\) minmax\(280px, 1\.2fr\);[^}]*padding: 12px 14px;[^}]*\}/,
    )
    expect(styles).toMatch(
      /\.mt-cmp-verdict-title\s*\{[^}]*overflow: hidden;[^}]*font-size: 18px;[^}]*text-overflow: ellipsis;[^}]*white-space: nowrap;[^}]*\}/,
    )
    expect(styles).toMatch(
      /\.mt-cmp-verdict p\s*\{[^}]*overflow: hidden;[^}]*-webkit-line-clamp: 2;[^}]*\}/,
    )
  })
})

describe('inline checkout styles', () => {
  test('keeps the checkout card compact and aligned with the conversation', () => {
    expect(styles).toMatch(
      /\.mt-ct-checkout\s*\{[^}]*align-self: flex-start;[^}]*width: 100%;[^}]*max-width: none;[^}]*padding: 13px;[^}]*\}/,
    )
    expect(styles).toMatch(
      /\.mt-ct-cogroup-actions > \.mt-ct-cobtn\s*\{[^}]*width: auto;[^}]*min-height: 34px;[^}]*margin: 0;[^}]*\}/,
    )
    expect(styles).toMatch(
      /\.mt-ct-checkout-brand-copy > strong\s*\{[^}]*font-size: 19px;[^}]*line-height: 1\.2;[^}]*\}/,
    )
    expect(styles).not.toContain('.mt-checkout-assistant-log')
  })

  test('stacks only the compact card header on small screens', () => {
    expect(styles).toMatch(
      /@media \(max-width: 640px\)\s*\{[\s\S]*?\.mt-ct-checkout-head\s*\{[^}]*flex-direction: column;[^}]*\}/,
    )
    expect(styles).toMatch(
      /@media \(max-width: 640px\)\s*\{[\s\S]*?\.mt-ct-checkout \.mt-ct-cogroup-head\s*\{[^}]*flex-flow: row wrap;[^}]*\}/,
    )
  })

  test('animates checkout progress without remounting the journey', () => {
    expect(styles).toMatch(
      /\.mt-checkout-steps > i::after\s*\{[^}]*transform: scaleX\(0\);[^}]*transition: transform 720ms[^}]*\}/,
    )
    expect(styles).toMatch(
      /\.mt-checkout-steps > div\.done \+ i::after\s*\{[^}]*transform: scaleX\(1\);[^}]*\}/,
    )
    expect(styles).toContain('@keyframes mt-checkout-progress-runner')
    expect(styles).toMatch(
      /@media \(prefers-reduced-motion: reduce\)\s*\{[\s\S]*?\.mt-checkout-steps > i::before\s*\{[^}]*display: none;[^}]*\}/,
    )
  })
})
