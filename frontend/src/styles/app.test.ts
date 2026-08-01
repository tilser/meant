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
