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

test('renders the persisted price currency in Account settings with USD as an option', () => {
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

  expect(markup).toContain('Price currency')
  expect(markup).toContain('USD is used by default')
  expect(markup).toContain('<option value="USD">USD — US Dollar</option>')
  expect(markup).toContain('<option value="EUR" selected="">EUR — Euro</option>')
})
