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
