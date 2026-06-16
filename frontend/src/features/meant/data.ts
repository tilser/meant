import type {
  CartItem,
  CorePreferenceId,
  DiscountCode,
  LocationOption,
  MerchantCoverage,
  Order,
  Preference,
  Product,
  Profile,
  Reply,
  UserAccount,
} from './types'

export const PREFERENCES: readonly Preference[] = [
  {
    id: 'organic',
    label: 'Organic',
    desc: 'Prefer certified-organic food and ingredients.',
  },
  {
    id: 'natural',
    label: 'Natural materials',
    desc: 'Clothing made from cotton, wool, linen, or other natural fibers.',
  },
  {
    id: 'no-poly',
    label: 'No polyester',
    desc: 'Avoid polyester and synthetic blends entirely.',
  },
  {
    id: 'reviews',
    label: 'Strong reviews',
    desc: 'Only show products people genuinely rate highly.',
  },
  {
    id: 'sustainable',
    label: 'Sustainable brands',
    desc: 'Favor brands with verified sustainability practices.',
  },
  {
    id: 'value',
    label: 'Best quality in budget',
    desc: 'The best quality I can get within my budget.',
  },
  {
    id: 'low-sugar',
    label: 'Low sugar',
    desc: 'Keep added sugar low across food and drink.',
  },
]

export const PROFILE: Profile = {
  name: 'Mara',
  greeting: 'Good afternoon',
  summary:
    'organic food, natural-material clothing, and products people actually love.',
}

export const DEFAULT_USER: UserAccount = {
  name: PROFILE.name,
  email: 'mara@meant.app',
  avatar: null,
}

export const PRODUCTS: readonly Product[] = [
  {
    id: 'cereal',
    name: 'Sprouted Oat & Almond Cereal',
    brand: 'Wholegrain Co.',
    category: 'Groceries',
    tone: '#e9ede8',
    match: 96,
    priceFrom: 7.4,
    merchants: 4,
    satisfies: ['organic', 'low-sugar', 'reviews'],
    misses: [],
    note: 'Organic, 3g sugar per serving, and the highest-rated cereal in your saved categories.',
    pros: ['No refined sugar', 'Whole sprouted grains', 'Recyclable packaging'],
    cons: ['Pricier than supermarket own-brand'],
    review: {
      score: 4.8,
      count: 2140,
      insight:
        "Reviewers consistently mention it keeps them full until lunch and isn't overly sweet.",
    },
    offers: [
      { merchant: 'Whole Foods', price: 7.4, delivery: 'Tomorrow' },
      { merchant: 'Thrive Market', price: 7.9, delivery: '2 days' },
      { merchant: 'iHerb', price: 8.2, delivery: '3 days' },
    ],
  },
  {
    id: 'tee',
    name: 'Heavyweight Organic Cotton Tee',
    brand: 'Field & Loom',
    category: 'Clothing',
    tone: '#e7ebef',
    match: 94,
    priceFrom: 38,
    merchants: 3,
    satisfies: ['natural', 'no-poly', 'sustainable', 'organic'],
    misses: [],
    note: '100% organic cotton, no synthetic blends, from a brand with verified sustainability practices.',
    pros: ['240gsm organic cotton', 'GOTS certified', 'Holds shape after wash'],
    cons: ['Runs slightly large'],
    review: {
      score: 4.7,
      count: 860,
      insight:
        'Buyers love the weight and fit; a few mention sizing up is unnecessary.',
    },
    offers: [
      { merchant: 'Field & Loom', price: 38, delivery: '3 days' },
      { merchant: 'Everlane', price: 42, delivery: '2 days' },
    ],
  },
  {
    id: 'brewer',
    name: 'Precision Pour-Over Brewer',
    brand: 'Kuro',
    category: 'Home & Kitchen',
    tone: '#eceae7',
    match: 91,
    priceFrom: 149,
    merchants: 5,
    satisfies: ['reviews', 'value'],
    misses: [],
    note: 'The best-reviewed brewer in your budget, with precise temperature control without the prosumer price.',
    pros: ['Exact temp control', 'Quiet operation', '10-year brand warranty'],
    cons: ['Takes counter space'],
    review: {
      score: 4.6,
      count: 3320,
      insight:
        'Owners praise consistency cup-to-cup; the main complaint is its footprint.',
    },
    offers: [
      { merchant: 'Kuro', price: 149, delivery: '4 days' },
      { merchant: 'Williams Sonoma', price: 159, delivery: '2 days' },
      { merchant: 'Amazon', price: 154, delivery: 'Tomorrow' },
    ],
  },
  {
    id: 'oil',
    name: 'Cold-Pressed Extra Virgin Olive Oil',
    brand: 'Casa Verde',
    category: 'Groceries',
    tone: '#eaede6',
    match: 89,
    priceFrom: 18.5,
    merchants: 3,
    satisfies: ['organic', 'reviews'],
    misses: [],
    note: 'Single-estate, organic, and an exceptional review profile for everyday cooking.',
    pros: ['Single-origin', 'Harvest date on bottle', 'Low acidity'],
    cons: ['Glass bottle is heavy to ship'],
    review: {
      score: 4.9,
      count: 1180,
      insight:
        'Described as fresh and peppery; reviewers repurchase frequently.',
    },
    offers: [
      { merchant: 'Casa Verde', price: 18.5, delivery: '3 days' },
      { merchant: 'Thrive Market', price: 19.2, delivery: '2 days' },
    ],
  },
  {
    id: 'sweater',
    name: 'Merino Crew Sweater',
    brand: 'Northbound',
    category: 'Clothing',
    tone: '#e8ebee',
    match: 92,
    priceFrom: 95,
    merchants: 4,
    satisfies: ['natural', 'no-poly', 'sustainable'],
    misses: [],
    note: 'Pure merino wool, no synthetic blend, from a B-Corp certified brand.',
    pros: ['Temperature regulating', 'No-itch fine merino', 'Mulesing-free wool'],
    cons: ['Hand wash recommended'],
    review: {
      score: 4.7,
      count: 540,
      insight: 'Praised for softness and warmth without bulk.',
    },
    offers: [
      { merchant: 'Northbound', price: 95, delivery: '3 days' },
      { merchant: 'Wool&', price: 110, delivery: '4 days' },
    ],
  },
  {
    id: 'runners',
    name: 'Recycled Knit Runners',
    brand: 'Stride Lab',
    category: 'Footwear',
    tone: '#eceaea',
    match: 71,
    priceFrom: 120,
    merchants: 3,
    satisfies: ['sustainable', 'reviews'],
    misses: ['no-poly'],
    note: 'Strong on sustainability and reviews, but the knit upper contains recycled polyester.',
    pros: ['Made from recycled ocean plastic', 'Very light', 'Machine washable'],
    cons: ['Contains polyester', 'Narrow toe box'],
    review: {
      score: 4.5,
      count: 2780,
      insight:
        'Loved for comfort and weight; some note the synthetic upper runs warm.',
    },
    offers: [
      { merchant: 'Stride Lab', price: 120, delivery: '2 days' },
      { merchant: 'Amazon', price: 124, delivery: 'Tomorrow' },
    ],
  },
  {
    id: 'laptop',
    name: 'Aero 13 Ultralight Laptop',
    brand: 'Lumen',
    category: 'Tech',
    tone: '#e9eaee',
    match: 90,
    priceFrom: 1099,
    merchants: 2,
    satisfies: ['reviews', 'value'],
    misses: [],
    provides: ['usb-c'],
    note: 'The best-reviewed ultralight in your budget. Note: it has USB-C ports only.',
    pros: ['18-hour battery', '1.1kg', 'USB-C / Thunderbolt'],
    cons: ['No USB-A or HDMI ports'],
    review: {
      score: 4.7,
      count: 5120,
      insight:
        'Praised for battery and weight; a few note the USB-C-only ports need adapters.',
    },
    offers: [
      { merchant: 'Lumen Store', price: 1099, delivery: '3 days' },
      { merchant: 'Amazon', price: 1119, delivery: 'Tomorrow' },
    ],
  },
  {
    id: 'monitor',
    name: '27" 4K USB-C Monitor',
    brand: 'Crisp',
    category: 'Tech',
    tone: '#e8ebee',
    match: 88,
    priceFrom: 389,
    merchants: 2,
    satisfies: ['reviews', 'value'],
    misses: [],
    needs: 'usb-c',
    note: 'Single-cable USB-C monitor that charges your laptop while you work.',
    pros: ['4K at 60Hz', 'USB-C single cable', '90W passthrough charging'],
    cons: ['Stand takes desk space'],
    review: {
      score: 4.6,
      count: 1840,
      insight:
        'Reviewers love the single-cable setup and color accuracy.',
    },
    offers: [
      { merchant: 'Crisp Store', price: 389, delivery: '3 days' },
      { merchant: 'Amazon', price: 399, delivery: 'Tomorrow' },
    ],
  },
  {
    id: 'drive',
    name: 'Rugged 2TB Portable Drive',
    brand: 'Hold',
    category: 'Tech',
    tone: '#eceae9',
    match: 84,
    priceFrom: 125,
    merchants: 2,
    satisfies: ['reviews'],
    misses: [],
    needs: 'usb-a',
    note: 'Fast, shock-proof backup drive that connects over USB-A.',
    pros: ['IP54 dust and water resistant', '5-year warranty', '1050MB/s'],
    cons: ['USB-A connector only'],
    review: {
      score: 4.6,
      count: 990,
      insight:
        'Trusted for durability; the USB-A-only cable trips up newer-laptop owners.',
    },
    offers: [
      { merchant: 'Amazon', price: 129, delivery: 'Tomorrow' },
      { merchant: 'Hold Store', price: 125, delivery: '4 days' },
    ],
  },
  {
    id: 'adapter',
    name: 'USB-C to USB-A Adapter',
    brand: 'Lumen',
    category: 'Tech',
    tone: '#eaeaec',
    match: 86,
    priceFrom: 19,
    merchants: 2,
    satisfies: ['value'],
    misses: [],
    needs: 'usb-c',
    provides: ['usb-a'],
    note: 'Plugs into USB-C and adds a full-size USB-A port for older accessories.',
    pros: ['Plug-and-play', 'Aluminium body', 'Pocket-sized'],
    cons: ['One port only'],
    review: {
      score: 4.5,
      count: 3400,
      insight: 'Reviewers call it a must-have for USB-C-only laptops.',
    },
    offers: [
      { merchant: 'Lumen Store', price: 19, delivery: '3 days' },
      { merchant: 'Amazon', price: 21, delivery: 'Tomorrow' },
    ],
  },
]

export const PROMPTS: readonly string[] = [
  'Find me a healthy breakfast cereal',
  'A good cotton T-shirt under $50',
  'Which coffee machine is best for me?',
  'Why is this recommended for me?',
]

export const REPLIES: Readonly<Record<string, Reply>> = {
  default: {
    text: "Here's what I found, filtered to your preferences. I've hidden anything that clashes with your profile and surfaced the strongest matches first.",
    ids: ['cereal', 'tee', 'brewer', 'oil', 'sweater', 'runners'],
  },
  cereal: {
    text: "For breakfast I'd start with the Sprouted Oat & Almond Cereal. It's organic, just 3g of sugar, and the best-reviewed option in your saved categories.",
    ids: ['cereal', 'oil'],
  },
  tee: {
    text: 'Under $50, the Heavyweight Organic Cotton Tee is the cleanest match: 100% organic cotton, no synthetic blend, from a sustainable brand.',
    ids: ['tee', 'sweater'],
  },
  brewer: {
    text: 'The Precision Pour-Over Brewer is the best-reviewed machine inside your budget: precise temperature control without the prosumer price.',
    ids: ['brewer'],
  },
}

export const DISCOUNTS: Readonly<Record<string, readonly DiscountCode[]>> = {
  Amazon: [
    {
      code: 'SPRING15',
      label: '15% off orders over $300',
      type: 'percent',
      value: 15,
      min: 300,
    },
  ],
  'Lumen Store': [
    {
      code: 'LUMEN50',
      label: '$50 off your first order',
      type: 'fixed',
      value: 50,
    },
  ],
  'Whole Foods': [
    {
      code: 'FRESH10',
      label: '10% off groceries',
      type: 'percent',
      value: 10,
    },
  ],
  'Crisp Store': [
    {
      code: 'FREESHIP',
      label: 'Free delivery',
      type: 'shipping',
      value: 0,
    },
  ],
}

export const LOCATIONS: readonly LocationOption[] = [
  {
    country: 'United States',
    code: 'US',
    cities: [
      'New York',
      'Los Angeles',
      'San Francisco',
      'Chicago',
      'Austin',
      'Seattle',
      'Denver',
      'Miami',
    ],
  },
  {
    country: 'United Kingdom',
    code: 'UK',
    cities: ['London', 'Manchester', 'Edinburgh', 'Bristol'],
  },
  {
    country: 'Canada',
    code: 'CA',
    cities: ['Toronto', 'Vancouver', 'Montreal'],
  },
  {
    country: 'Germany',
    code: 'DE',
    cities: ['Berlin', 'Munich', 'Hamburg'],
  },
  {
    country: 'Australia',
    code: 'AU',
    cities: ['Sydney', 'Melbourne', 'Brisbane'],
  },
]

export const MERCHANTS: Readonly<Record<string, MerchantCoverage>> = {
  Amazon: { ships: 'global' },
  iHerb: { ships: 'global' },
  'Stride Lab': { ships: 'global' },
  'Thrive Market': { ships: ['US'] },
  'Whole Foods': {
    ships: ['US'],
    cities: [
      'New York',
      'Los Angeles',
      'San Francisco',
      'Chicago',
      'Austin',
      'Seattle',
    ],
  },
  'Field & Loom': { ships: ['US', 'CA', 'UK'] },
  Everlane: { ships: ['US', 'CA', 'UK', 'DE'] },
  Kuro: { ships: ['US', 'CA', 'UK', 'DE', 'AU'] },
  'Williams Sonoma': { ships: ['US', 'CA'] },
  'Casa Verde': { ships: ['US', 'UK', 'DE'] },
  Northbound: { ships: ['US', 'CA', 'UK', 'AU'] },
  'Wool&': { ships: ['US'] },
  'Lumen Store': { ships: ['US', 'CA', 'UK', 'DE', 'AU'] },
  'Crisp Store': { ships: ['US', 'UK', 'DE'] },
  'Hold Store': { ships: ['US', 'CA'] },
}

export const DEFAULT_SAVED: readonly Product[] = PRODUCTS.filter((product) =>
  ['cereal', 'tee', 'brewer'].includes(product.id),
)

export const DEFAULT_SAVED_IDS: readonly Product['id'][] = [
  'cereal',
  'tee',
  'brewer',
]

export const DEFAULT_COMPARE: readonly Product['id'][] = [
  'sweater',
  'runners',
]

export const DEFAULT_CART: readonly CartItem[] = [
  { id: 'laptop', merchant: 'Lumen Store', qty: 1 },
  { id: 'monitor', merchant: 'Amazon', qty: 1 },
  { id: 'drive', merchant: 'Amazon', qty: 1 },
  { id: 'cereal', merchant: 'Whole Foods', qty: 1 },
]

export const DEFAULT_ORDERS: readonly Order[] = [
  {
    id: 'MNT-4192',
    date: '2026-06-11',
    status: 'In transit',
    statusNote: 'Out for delivery: arrives Jun 16.',
    items: [{ id: 'brewer', merchant: 'Kuro', qty: 1 }],
    saved: 0,
    savedNote: '',
  },
  {
    id: 'MNT-3947',
    date: '2026-05-21',
    status: 'Delivered',
    statusNote: 'Delivered May 25.',
    items: [
      { id: 'tee', merchant: 'Field & Loom', qty: 2 },
      { id: 'sweater', merchant: 'Northbound', qty: 1 },
    ],
    saved: 0,
    savedNote: '',
  },
  {
    id: 'MNT-3610',
    date: '2026-04-28',
    status: 'Delivered',
    statusNote: 'Delivered May 1.',
    items: [
      { id: 'cereal', merchant: 'Whole Foods', qty: 2 },
      { id: 'oil', merchant: 'Casa Verde', qty: 1 },
    ],
    saved: 1.48,
    savedNote: 'FRESH10 · Whole Foods',
  },
  {
    id: 'MNT-3158',
    date: '2026-03-09',
    status: 'Delivered',
    statusNote: 'Delivered Mar 13.',
    items: [
      { id: 'laptop', merchant: 'Lumen Store', qty: 1 },
      { id: 'adapter', merchant: 'Lumen Store', qty: 1 },
    ],
    saved: 50,
    savedNote: 'LUMEN50 · Lumen Store',
  },
]

export const CORE_PREFERENCE_IDS: readonly CorePreferenceId[] = [
  'organic',
  'natural',
  'no-poly',
  'reviews',
  'sustainable',
  'value',
  'low-sugar',
]
