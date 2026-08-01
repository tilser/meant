const MAX_SIMILARITY_QUERY_LENGTH = 500

export function similaritySearchQuery(
  originatingQuery: string | undefined,
  productName: string,
): string {
  const query = originatingQuery?.trim() || `products similar to ${productName}`
  return query.slice(0, MAX_SIMILARITY_QUERY_LENGTH)
}
