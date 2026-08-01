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
