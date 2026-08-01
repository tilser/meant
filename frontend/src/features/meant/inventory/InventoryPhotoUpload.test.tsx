import { describe, expect, test } from 'bun:test'
import { renderToStaticMarkup } from 'react-dom/server'

import { InventoryPhotoUpload } from './InventoryPhotoUpload'

describe('InventoryPhotoUpload', () => {
  test('shows the selected filename with app-owned copy and native file semantics', () => {
    const markup = renderToStaticMarkup(
      <InventoryPhotoUpload
        id="inventory-photo"
        label="Photo *"
        actionLabel="Choose photo"
        emptyStatus="No photo selected"
        file={new File(['photo'], 'blue-shirt.webp', { type: 'image/webp' })}
        required
        onChange={() => undefined}
      />,
    )

    expect(markup).toContain('type="file"')
    expect(markup).toContain('accept="image/jpeg,image/png,image/webp"')
    expect(markup).toContain('required=""')
    expect(markup).toContain('for="inventory-photo">Choose photo</label>')
    expect(markup).toContain('role="status">blue-shirt.webp</span>')
    expect(markup).toContain('JPEG, PNG, or WebP · 5 MB maximum')
  })
})
