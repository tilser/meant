# Frontend test plan — zadání pro Codex

Cíl: pokrýt testy logiku, kde tichá regrese = uživatel se zasekne nebo přijde
o data. Priorita je **stale-cart recovery** (právě opravená, zatím netestovaná)
a **error-handling boundary** v `apiClient`, pak postupně zbytek pure logiky v
`utils.ts`.

## Prostředí (důležité — drž se toho)

- **Runner: Bun built-in** (`bun test`). Existující test
  `src/features/meant/utils.test.ts` importuje `import { describe, expect, test } from 'bun:test'`.
  Nepřidávej vitest/jest/RTL — nejsou v projektu a nechceme novou test
  infrastrukturu.
- Přidej do `package.json` script `"test": "bun test"`, ať to běží jednotně a
  jde to zapojit do CI.
- **React komponenty netestujeme** (RTL/jsdom v projektu nejsou). Veškerá logika
  k testování musí být čistá funkce. Kde dnes žije jako closure uvnitř
  `MeantApp.tsx`, je nutný malý **extract-and-export refactor** (viz níže) —
  refactor je součást zadání, ne vedlejší efekt.
- Žádné síťové volání v testech. Kde je potřeba `fetch`/`updateCart`, předávej
  závislost jako parametr nebo mockuj na úrovni modulu.

---

## Blok 1 — apiClient error boundary (NEJVYŠŠÍ PRIORITA)

Soubor: `src/lib/apiClient.ts`. Tady vznikla minulá tichá chyba (FE matchoval na
text chyby, který backend scrubuje). Testy to musí zafixovat napevno.

### 1A. `parseErrorResponse` → typed `ApiError`
`parseErrorResponse` je dnes **private**. Exportuj ji (nebo přesuň do
`src/lib/apiError.ts` spolu s `ApiError` a reexportuj). Pak testuj:

- [ ] **Status se propisuje**: ProblemDetail body se statusem 404 → vrácená
  `ApiError` má `status === 404`.
- [ ] **`code` se propisuje**: body `{ code: "not_found", detail: "..." }` →
  `error.code === "not_found"`.
- [ ] **`detail` má přednost před `title`/`message`** při skládání `message`
  (zachovat existující prioritu `detail || message || title || fallback`).
- [ ] **Fallback při ne-JSON těle**: response, jejíž `.json()` hodí → vrátí
  `ApiError(fallback, status, null)`, nikdy nezahučí.
- [ ] **Chybějící `code`**: body bez `code` → `error.code === null` (ne crash,
  ne `undefined` přetékající jinam).
- [ ] `ApiError instanceof Error === true` (regrese: ostatní handlery čtou
  `error.message`).

### 1B. `parseJsonResponse`
- [ ] **2xx**: vrátí naparsované tělo jako `T`.
- [ ] **ne-2xx hází `ApiError`** (ne plain `Error`), s korektním `status`.
- [ ] Hozená chyba nese `message` z `detail`, ne fallback, když `detail` existuje.

> Pozn.: `parseJsonResponse` bere `Response`. V Bunu jde sestavit
> `new Response(JSON.stringify(body), { status, headers: { 'content-type': 'application/json' } })`
> — žádný mock fetch netřeba.

---

## Blok 2 — Stale-cart recovery (VYSOKÁ PRIORITA, právě opravené)

Logika je v `MeantApp.tsx` jako closures (`isCartNotFoundError`,
`recreateMerchantCart`, `cartAddItemsForMerchant`, `clearMerchantCartState`) a
v pure helperech (`mergeCartSnapshot`, `cartMerchantKey`). Closures sahají na
React state, takže nejsou přímo testovatelné.

### Prerequisite refactor (součást zadání)
Vytáhni čistou, na Reactu nezávislou jádrovou logiku do `utils.ts` (nebo nového
`cartRecovery.ts`) a exportuj:

1. `isCartNotFoundError(error: unknown): boolean` — přesuň beze změny chování
   (dnes: `error instanceof ApiError && (error.status === 404 || error.code === 'not_found')`).
2. `cartRebuildItems(items, merchantKey)` — čistá verze logiky z
   `cartAddItemsForMerchant` / `recreateMerchantCart`: z položek daného
   merchanta vyrob `{ productVariantId, quantity }[]`, vyfiltruj bez
   `productVariantId`, `quantity = max(qty, 1)`.
3. `mergeCartSnapshot` a `cartMerchantKey` už pure jsou — jen je exportuj.

Closures v `MeantApp` pak ať volají tyhle exportované funkce (žádná duplicitní
logika).

### Testy: `isCartNotFoundError`
- [ ] `ApiError` se `status: 404` → **true**.
- [ ] `ApiError` s `code: "not_found"` a jiným statusem → **true**.
- [ ] `ApiError` se `status: 400` / `code: "bad_request"` → **false**.
- [ ] Plain `new Error("cart not found")` → **false** (regrese: NESMÍ se znovu
  spoléhat na text! Tohle je přesně ta díra z minula).
- [ ] `undefined` / `null` / `{}` → **false**, nehází.

### Testy: `cartRebuildItems`
- [ ] Vybere jen položky daného `merchantKey`.
- [ ] Vynechá položky bez `productVariantId`.
- [ ] `qty: 0` nebo záporné → `quantity: 1` (clamp).
- [ ] Zachová správné `qty` u validních položek (regrese pro `updateQty`
  recovery: rebuild musí nést aktuální množství).
- [ ] Prázdný vstup pro merchanta → `[]` (volající pak ví, že není co
  obnovovat).

### Testy: `mergeCartSnapshot`
- [ ] Aktualizuje jen položky odpovídajícího `merchantKey`, ostatní nechá beze
  změny.
- [ ] `cartId`/`continueUrl`/`checkoutUrl` ze snapshotu přepíšou staré hodnoty;
  když snapshot pole nemá (`undefined`), padne se na původní hodnotu položky.
- [ ] `qty` se vezme z `line.quantity` snapshotu, jinak zůstane `item.qty`.
- [ ] `syncing`/`syncError` se po merge vždy vynulují.
- [ ] `deliveryGroups`: null prvky se vyfiltrují; prázdný snapshot zachová
  původní groups, ne `undefined`.

---

## Blok 3 — Checkout handoff URL (STŘEDNÍ priorita)

Helper `firstUrl` v `MeantApp.tsx` (private) — exportuj do `utils.ts`. Pohání
volbu mezi `continueUrl` a `checkoutUrl` (commit „use merchant checkout handoff
URL").

### Testy: `firstUrl`
- [ ] Vrátí první neprázdnou hodnotu v pořadí argumentů.
- [ ] Přeskočí `null`, `undefined`, prázdný řetězec, a string složený jen z
  whitespace.
- [ ] Vrácenou hodnotu **trimuje**.
- [ ] Vše prázdné → `null`.

> Pokud se rozhodneš testovat i preferenci `continueUrl` před `checkoutUrl` v
> checkout flow, je nutné vytáhnout výběrovou logiku z `checkout()` do čisté
> funkce `resolveHandoffUrl(profile, payload)`. Volitelné — flag to v PR, ať se
> rozhodne, jestli to stojí za extract.

---

## Blok 4 — Pure logika v `utils.ts` (PRŮBĚŽNĚ, nižší priorita)

`utils.ts` má 33 exportovaných čistých funkcí, testované jsou 3
(`cartGroups`, `cartDeliveryOptions`, `displayProductCategoryValue`). Doplnit
testy pro ty, kde chyba mění nákupní rozhodnutí nebo peníze:

- [ ] **`bestOffer`** — vybere nejlevnější dostupnou nabídku pro dané lokality;
  remízy; žádná dostupná nabídka.
- [ ] **`bestCode`** — výběr nejlepšího slevového/dárkového kódu.
- [ ] **`productPriceFrom`** / **`productMerchantCount`** — agregace přes
  nabídky, hraniční případy (0 nabídek).
- [ ] **`canMerchantShip`** / **`productsForLocation`** — filtrování podle
  doručitelnosti do lokality.
- [ ] **`productsForPreferences`** — filtrování podle preferencí (pozitivní i
  negativní polarita).
- [ ] **`productMatchesClothingFit`** / **`productsForClothingFit`** — men /
  women / other.
- [ ] **`computeSmartAlerts`** — generování upozornění (lepší cena, kód, …).
- [ ] **`cartLines`** — skládání řádků košíku z položek.
- [ ] **`orderTotal`** — součet objednávky (peníze → ověřit, že nesčítá float
  chybně; ideálně přes minor units).
- [ ] **`createOrder`** — mapování `CheckoutPayload` → `Order`.
- [ ] **`deriveFilters`** / **`resolveAsk`** / **`resolveReply`** — parsování
  textu na filtry/odpovědi (deterministická část).
- [ ] **`readStorage`/`writeStorage`** — round-trip, fallback při rozbitém JSON
  v localStorage (nesmí zhodit appku).
- [ ] **`money`** — formátování (měna, desetinná místa, záporné).
- [ ] **`listJoin`** / **`prefLabel`** — drobné, ale levné na pokrytí.

---

## Co NEtestovat

- Rendering komponent `MeantApp`, routy, supabase auth — bez RTL/jsdom mimo
  rozsah.
- Skutečná HTTP volání na backend.
- `schema.d.ts` (generované) a `routeTree.gen.ts` (generované).

## Akceptační kritéria

- `bun test` projde zeleně, včetně stávajícího `utils.test.ts`.
- `npm run typecheck` a `npm run build` zůstanou čisté (refactory nesmí rozbít
  importy).
- Žádná logika se nezduplikuje: closures v `MeantApp` po extractu volají
  exportované funkce, ne svoji kopii.
- Každý extract = čistá funkce bez závislosti na React state / `window` (kromě
  `readStorage`/`writeStorage`, kde se `localStorage` mockuje).
