import type { Product } from '../types'

export interface MerchantProductDetailRequest {
  merchantId: string
  productId: string
}

export function merchantProductDetailRequest(
  product: Product | null,
): MerchantProductDetailRequest | null {
  if (product?.rehydratedDetails || !product?.remote) {
    return null
  }

  const merchantId = product.merchantId?.trim()
  const productId = product.merchantProductId?.trim()
  return merchantId && productId ? { merchantId, productId } : null
}
