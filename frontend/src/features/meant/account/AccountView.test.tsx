import { expect, mock, test } from 'bun:test'
import { renderToStaticMarkup } from 'react-dom/server'

mock.module('../../../lib/apiClient', () => ({
  deleteProfilePictureFile: async () => undefined,
  removeProfilePicture: async () => ({}),
  updateProfile: async () => ({}),
  updateProfilePicture: async () => ({}),
  uploadProfilePictureFile: async () => ({}),
}))

const { AccountView } = await import('./AccountView')

test('renders the persisted preferred display currency and explains merchant currency fallback', () => {
  const markup = renderToStaticMarkup(
    <AccountView
      user={{
        name: 'Meant User',
        email: 'user@example.com',
        avatar: null,
        avatarPath: null,
        newsletter: false,
      }}
      currency="EUR"
      userId="user-1"
      providerAvatar={null}
      merchants={[]}
      merchantIdentityLinks={[]}
      merchantIdentityLinksLoading={false}
      merchantIdentityLinksError={null}
      onSave={() => undefined}
      onSignOut={() => undefined}
      onEditPrefs={() => undefined}
      onConnectMerchant={() => undefined}
      onRevokeMerchant={() => undefined}
      onNewsletterChange={() => undefined}
      onCurrencyChange={() => undefined}
      onDone={() => undefined}
    />,
  )

  expect(markup).toContain('Preferred display currency when available')
  expect(markup).toContain(
    'Meant always asks merchants for catalog prices in the currency you select.',
  )
  expect(markup).toContain('We cannot enforce this preference.')
  expect(markup).toContain(
    'If a merchant returns a price in a different currency, Meant shows the currency the merchant provides. Merchants usually choose that currency based on your shipping location.',
  )
  expect(markup).toContain('class="mt-acct-currency-note"')
  expect(markup).toContain('Preferred currency')
  expect(markup).toContain('class="mt-field mt-acct-currency-field"')
  expect(markup).toContain('class="mt-acct-save-row mt-acct-currency-actions"')
  expect(markup).toContain('<option value="USD">USD — US Dollar</option>')
  expect(markup).toContain('<option value="EUR" selected="">EUR — Euro</option>')
})
