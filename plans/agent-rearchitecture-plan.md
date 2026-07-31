# Přestavba shopping agenta na skutečný AI agent

**Stav:** návrh k implementaci
**Datum:** 2026-07-31
**Rozsah:** `backend/src/main/java/com/meant/api/module/agent/**`, `common/service/SpringAiAgentModelGateway.java`, `common/config/AgentModelConfiguration.java`

---

## 1. Shrnutí

Meant má funkční model gateway (Spring AI 2.0.0 → OpenRouter) i funkční tool-calling smyčku. Co chybí, je **agent**: kolem té smyčky je postavená deterministická vrstva, která modelu bere rozhodování a nahrazuje ho anglickými regulárními výrazy. Model často nemá tool, který by potřeboval, protože mu ho regex nepustil; nebo se model vůbec nespustí, protože regex sestavil tool call sám.

Cíl přestavby: **model rozhoduje co a kdy; server vynucuje, že cíl akce je reálný a patří uživateli.** Žádné rozhodování na základě shody slov.

Rozhodnutí, ze kterých návrh vychází (potvrzená):

| # | Rozhodnutí |
|---|---|
| A | Approve od uživatele pouze u checkoutu. U všech ostatních akcí plně věříme modelu. |
| B | Jazyková agnostika je tvrdý požadavek. Všechny regexy nad uživatelským textem se mažou. |
| C | `AgentRunCoordinator` zůstává. Spring AI se využije hlouběji tam, kde to dává smysl (viz §6). |
| D | Testy zamykající regexové chování se přepíší. |
| E | Žádné hardcoded uživatelsky viditelné stringy. Veškerý text generuje model. |
| F | Filtry maximalizují relevanci, ale které jsou pro daný request potřeba, rozhoduje model. Qualification vrstva je poradce, ne brána (§7). |

---

## 2. Diagnóza současného stavu

### 2.1 Regex rozhoduje, které tooly model uvidí

`AgentToolAuthorizationPolicy.available()` (řádek 186) filtruje seznam toolů podle patternů nad uživatelským textem — `CART_ADDITION_INTENT`, `PIN_INTENT`, `CHECKOUT_PREPARATION_INTENT`, `MISSION_CREATION_INTENT` a dalších ~25 patternů.

Důsledek: uživatel napíše „hoď mi to do košíku" nebo „přidej dvojku" a `add_cart_line` v tool listu **není**. Model nemá jak vyhovět. Následně `AgentRunCoordinator:324` detekuje, že žádná mutace neproběhla, a pošle modelu systémovou výtku (`missingMutationCorrection`) za něco, co nemohl udělat. Model buď halucinuje úspěch, nebo skončí obecnou omluvou. **Toto je hlavní příčina nespolehlivosti.**

Patterny jsou navíc čistě anglické (`\b(?:add|put|place)\b.*\b(?:cart|basket|bag)\b`). Pro US+EU trh je to fatální.

### 2.2 Regex obchází model úplně

`AgentRunCoordinator:269` — na první iteraci volá `readIntentResolver.resolve()`, který z patternů (`EXPLICIT_PRODUCT_SEARCH_INTENT`, `FIND_SIMILAR_INTENT`, `BARE_PRODUCT_QUERY_DISQUALIFIER`, `NON_PRODUCT_BARE_HEADS`) sestaví tool call **bez modelu** a v části případů run rovnou ukončí hardcoded větou („I found these options:").

`AgentProductClarificationService.unresolvedIntent()` (volané na řádku 294) umí run ukončit otázkou „Which product should I pin?", aniž model kdy běžel.

`requiresComparisonArtifact()` (řádek 603) na základě regexu `^compare` forsuje `tool_choice` na konkrétní tool.

### 2.3 Druhá regexová brána nad argumenty

`authorizedInvocation()` → `AgentMutationTargetPolicy` (1086 řádků) + `AgentMutationTargetTextSupport` (360 řádků). Porovnává ordinály a tokeny z uživatelského textu proti artefaktům. Model může zavolat správný tool se správným, server-vydaným `offerKey` a stejně to spadne, protože se textově neshodne.

### 2.4 Kontext ztrácí výsledky toolů

`AgentContextAssembler:183` nahradí každou TOOL zprávu z minulých tahů placeholderem:

```java
return AgentModelMessage.system(
        "A prior verified tool produced typed artifacts in the server-issued artifact index. ...");
```

Model tedy napříč tahy nevidí, co mu tooly vrátily. Zbytek kontextu je přitom udělaný dobře — viditelné product cards s ordinály, cart state, artifact index (§4.2 na tom staví).

### 2.5 System prompt kompenzuje deterministickou vrstvu

~90 řádků pravidel. Značná část existuje jen proto, aby model nekolidoval s regexy („Do not ask a size, destination, price... question yourself", „Never invent or reconstruct a qualificationId"). Po odstranění vrstvy většina pravidel ztrácí smysl.

---

## 3. Cílová architektura

```
SubmitAgentTurn
      │
      ▼
AgentRunCoordinator ──── zachováno beze změny ────┐
  · run leasing / heartbeat                       │
  · per-conversation lane                         │  orchestrace běhu
  · cancelace                                     │  (mimo záběr Spring AI)
  · paralelní read tooly                          │
  · SSE streaming                                 │
  · tool invocation audit                         │
      │                                           │
      ├── AgentContextAssembler ──── §4 přepis ────┘
      │     system prompt + historie + tool results
      │
      ├── modelGateway.turn() ──── §6 ────────────── Spring AI
      │     všechny tooly, vždy
      │
      ├── AgentToolCallExecutor
      │     └── ReferenceIntegrityPolicy ── §5 ──── nahrazuje 1450 ř. regexů
      │           ownership + server-issued reference
      │
      └── checkout ── §5.3 ── user approve gate
```

Co **mizí**:

| Soubor | Řádků | Osud |
|---|---|---|
| `AgentReadIntentResolver` | 325 | smazat |
| `AgentProductClarificationService` | 251 | smazat |
| `AgentProductClarificationContextService` | 65 | smazat |
| `AgentMutationTargetPolicy` | 1086 | nahradit `ReferenceIntegrityPolicy` (~150 ř.) |
| `AgentMutationTargetTextSupport` | 360 | smazat |
| `AgentToolAuthorizationPolicy` | 502 | zredukovat na ~60 ř. (jen risk-class + checkout gate) |
| `AgentProductSearchQualificationContinuationPolicy` | 247 | smazat (§7.1) |
| `AgentRunCoordinator` — regexové části | ~120 | smazat |

Celkem ~2950 řádků pryč, ~250 přibude.

Co **zůstává beze změny**: `AgentRunService`, `AgentToolInvocationService`, `AgentArtifactService`, `AgentMessageLedgerService`, `AgentRunEventNotifier`, `AgentMetrics`, celý `repository` a `entity` package, všechny tooly v `service/tool/` kromě jejich descriptorů.

---

## 4. Kontext pro model

### 4.1 System prompt

Zkrátit z ~90 řádků na ~25. Odstranit vše, co popisuje chování zrušené deterministické vrstvy. Ponechat:

- identitu (shopping agent pro autentizovaného uživatele),
- popis, co znamenají server-vydané artefakty a stable keys,
- pravidlo, že ID se nikdy nevymýšlejí,
- trust boundary (labely jsou untrusted data, ID autoritativní),
- omezení checkoutu (agent připraví, nedokončí — §5.3),
- pokyn odpovídat v jazyce uživatele.

Odstranit zejména: všechna pravidla o `search_catalog` qualification flow (§7), `WAITING_FOR_USER` konvenci (§4.3), pravidla o tom, kdy se smí ptát na velikost/destinaci/cenu, a pravidla o ordinálech (model je uvidí v kontextu).

### 4.2 Historie s tool results

`AgentContextAssembler.historicalMessage()` — nahradit placeholder skutečnými tool results. Model musí vidět, co mu tooly vrátily v minulých tazích; jinak nemůže odkazovat na „ty boty, cos mi ukázal".

Rozpočet: tool results jsou objemné. Návrh — plné results pro poslední 2 tahy, pro starší jen `AgentArtifactReference` index (ten už existuje a funguje). Řídit přes stávající `contextCharacterBudget`.

Zachovat současné trust-boundary značení: viditelné product cards s ordinály, cart state, shelf snapshot. Tato část je dobře udělaná a je přesně to, co uživatel v zadání požadoval („aby věděl, na co uživatel odkazuje").

### 4.3 Zrušení `WAITING_FOR_USER`

Textová konvence `WAITING_FOR_USER: <otázka>` je jazykově závislý protokol vynucený promptem. Model se má ptát přirozeně; run končí prostě tím, že model nevrátí žádný tool call. Příznak „čeká se na uživatele" se odvodí ze stavu, ne z prefixu v textu.

Dotčené: `AgentRunCoordinator` řádky 60, 450, 738–748, plus 4 zmínky v system promptu.

---

## 5. Bezpečnostní model

Regexy nahrazuje **referenční integrita**. Princip: model smí zavolat jakýkoli tool s jakýmikoli argumenty, ale každá reference v argumentech musí být artefakt, který server v této konverzaci sám vydal.

### 5.1 `ReferenceIntegrityPolicy`

Nová třída (~150 ř.), nahrazuje `AgentMutationTargetPolicy`. Kontrakt:

```
validate(context, toolName, canonicalArgumentsJson) -> ok | rejection(reason)
```

Kontroly:

1. **Ownership** — každé ID v argumentech (`offerKey`, `cartId`, `cartLineId`, `canonicalProductKey`, `inventoryItemId`, `missionId`) musí existovat v `AgentArtifactReference` pro tuto `conversationId` + `userId`. To je čistý DB lookup, žádný text.
2. **Aktuálnost** — `cartId` / `cartLineId` musí být v aktuálním cart snapshotu, ne v historickém. `AgentCartSnapshotSupport.project()` už tuto projekci umí.
3. **Schema** — `AgentToolSchemaValidator` (existuje, beze změny).
4. **Idempotence** — `AgentToolInvocationService` (existuje, beze změny).

Při odmítnutí se modelu vrátí strukturovaná chyba jako tool result (`{"error":"reference_not_found","field":"offerKey"}`), ne hardcoded věta uživateli. Model si poradí sám — zavolá read tool a zkusí znovu. Tím se řeší i bod E.

### 5.2 Co se tím ztrácí a proč je to v pořádku

Regexy chytaly případ „model si vymyslí, že uživatel chtěl přidat do košíku". Referenční integrita tento případ nechytá — model může zavolat `add_cart_line` s platným `offerKey`, i když si to uživatel nepřál.

Podle rozhodnutí A to akceptujeme. Zmírnění, které to činí bezpečným:

- **cart není nevratný** — uživatel vidí obsah košíku v UI a může položku odebrat,
- **checkout je za approve gate** (§5.3), takže halucinace nikdy nevede k platbě,
- `AgentToolRegistry` už dnes tvrdě odmítá registraci `IRREVERSIBLE_MUTATION` toolů a `complete_checkout` (řádek 22) — **toto zůstává**,
- `AgentToolInvocationService` audituje každé volání, takže je vždy dohledatelné, co agent udělal.

### 5.3 Checkout approve gate

Jediné místo, kde se model nesmí rozhodnout sám. Mechanismus — využít existující `AgentUserAction` infrastrukturu (`AgentUserActionService`, `AgentUserActionStatus`, tabulka `agent_user_actions`), která už dnes umožňuje, aby akci spustil uživatel z UI, ne model.

Návrh:

1. `prepare_checkout` **zůstává** modelu dostupný — připraví checkout, vrátí typovaný artefakt.
2. Artefakt se v UI vyrenderuje jako karta s explicitním potvrzovacím tlačítkem.
3. Potvrzení jde přes `AgentUserActionService.perform()` — tedy z uživatelova kliknutí, s vlastní rezervací a idempotencí, mimo model.
4. `AgentToolAuthorizationPolicy` se zredukuje na jedinou kontrolu tohoto typu: tool s risk class vyšší než `MUTATION` smí spustit pouze user action lane, nikdy model.

Tím je gate strukturální, ne textový — nezávislý na jazyce i na tom, co model napíše.

---

## 6. Využití Spring AI

Ověřeno proti dokumentaci Spring AI 2.0.0 (context7, `spring_io_spring-ai_reference_2_0-snapshot` a `spring_io_spring-ai_2_0_0`).

### 6.1 Co Spring AI nepokrývá — a proto coordinator zůstává

Spring AI vlastní vrstvu „zavolej model, předej tooly, dostaň odpověď". Orchestrace běhu je mimo jeho záběr:

| Mechanismus | K čemu je | Spring AI |
|---|---|---|
| Run leasing (`claim()` → `executionOwner`) | Atomický přechod QUEUED→RUNNING. Na Railway běží víc instancí; bez toho by dvě instance vzaly stejný run a uživatel dostal dvě odpovědi. | ne |
| Heartbeat | Obnova lease za běhu. Když instance spadne, lease vyprší a run lze převzít místo aby visel v RUNNING. | ne |
| Cancelace | Uživatel klikne stop → flag v DB → `BooleanSupplier` až do gateway ukončí stream uprostřed. | částečně (`Flux` lze odunsubscribovat, ale cross-instance signál je vlastní) |
| Per-conversation lane | Jeden běžící run na konverzaci. Bez toho druhý run čte kontext dřív, než první zapsal výsledky. | ne |
| Paralelní read tooly | READ tooly z jednoho tahu paralelně na virtuálních vláknech, mutace sériově. Pět katalogových dotazů v čase jednoho. | částečně (`ToolCallingManager` provede tool calls, ale sériově a bez rozlišení read/mutace) |
| SSE streaming s fallback-safety | `DeltaWriter` bufferuje 96 znaků; jakmile je první token odeslán, nelze přepnout na fallback model. | ne (`Flux` ano, perzistence eventů a fallback-safety ne) |
| Tool invocation audit | Perzistence volání + extrakce typovaných artefaktů se stable keys. Zdroj pravdy pro UI i referenční integritu. | ne (observability ano, doménová perzistence ne) |

### 6.2 Co ze Spring AI použít hlouběji

**Manuální tool-execution smyčka je oficiálně podporovaný vzor.** Dokumentace 2.0.0 ho explicitně doporučuje pro „observable and interruptible iterations":

```java
while (response.chatResponse() != null && response.chatResponse().hasToolCalls()) {
    ToolExecutionResult result = toolCallingManager.executeToolCalls(prompt, response.chatResponse());
    prompt = new Prompt(result.conversationHistory(), chatOptions);
    // ...
}
```

To je přesně to, co `AgentRunCoordinator` dělá. **Současná architektura je v souladu s doporučeným vzorem Spring AI**, ne proti němu.

Konkrétní zlepšení:

1. **`ToolCallingManager.executeToolCalls()`** místo ruční správy `modelMessages`. Vrací `conversationHistory()` se správně poskládanými assistant/tool zprávami — odpadne ruční skládání v `AgentRunCoordinator:348` a `:436` a s ním třída chyb kolem párování `toolCallId`.

2. **`ToolExecutionEligibilityChecker`** místo ručního `calls.isEmpty()` na řádku 323.

3. **`ToolCallback` s reálným `call()`** místo `SchemaOnlyToolCallback` (dnes vyhazuje `IllegalStateException`, řádek 205). Tooly se stanou skutečnými Spring AI tooly. Pozor: `ToolCallingManager` provádí tool calls sériově — paralelizaci READ toolů (§6.1) je nutné zachovat vlastní implementací `ToolCallingManager` nebo ponechat současný `executeTools()`. **Doporučení: ponechat `executeTools()`**, protože paralelizace a řazení read/mutace je měřitelná hodnota.

4. **`ToolCallingChatOptions`** místo `OpenAiChatOptions` v gateway — provider-agnostické, usnadní případný přechod z OpenRouter.

### 6.3 Co ze Spring AI nepoužít

- **`ChatClient` + `ToolCallingAdvisor`** — auto tool-execution obejde `AgentToolInvocationService` (audit, idempotence, artefakty) a paralelizaci. Zůstat u `ChatModel` přímo, jak dokumentace pro tento případ doporučuje.
- **`ChatMemory`** — neumí artefaktový kontext s ordinály a cart state. `AgentContextAssembler` je bohatší.
- **`StreamingToolCallBuilder`** (Bedrock-specific) — vlastní `ToolCallCollector` v gateway řeší přesně dokumentovaný problém OpenAI-kompatibilních providerů (chybějící `id`/`name` v pozdějších fragmentech). Ponechat.

---

## 7. Search qualification: filtry jako doporučení, ne jako brána

**Rozhodnuto.** Motivace je relevance výsledků: čím víc filtrů je vyplněných, tím lepší. Model o tom musí vědět, ale **které filtry jsou pro daný request potřeba, rozhoduje model** podle toho, co uživatel napsal.

Z toho plyne obrácení současného vztahu. Dnes je qualification vrstva **brána** — když chybí filtr, `SearchCatalogAgentTool:146` vrátí `qualificationRequired(...)` a run skončí serverem složenou otázkou. Nově je **poradce**: vždy vyhledá a k výsledkům přiloží stav filtrů. Model se podívá a rozhodne, jestli se doptá, nebo výsledky rovnou ukáže.

### 7.1 Co se ruší

- `AgentProductSearchQualificationContinuationPolicy` (247 ř.) — regexy hádající, zda krátká odpověď „46" je odpověď na otázku, nové zadání, nebo zrušení. Model to vyřeší z historie, protože otázku i odpověď vidí (§4.2).
- **Pravomoc přerušit run.** `qualificationRequired(...)` větev v `SearchCatalogAgentTool` mizí; `assistantMessage` z plánu se přestává používat jako text pro uživatele (bod E — text píše model).
- Parametry `qualificationId` a `qualificationUpdatedAt` ze schématu toolu. Model už nemusí přenášet server-vydaný token mezi tahy — qualification se váže na konverzaci. Odpadá s tím i celý blok pravidel v system promptu („Never invent or reconstruct a qualificationId").

### 7.2 Co zůstává

Celá `UserProductSearchQualificationService` a persistence plánu. Tato vrstva je hodnotná a v tomto návrhu se nemění:

- extrakce filtrů z konverzace a profilu,
- persistence napříč konverzacemi (`DurableAttribute`),
- mapování na Shopify UCP parametry (`UserProductSearchQualificationPlanMapper`),
- rozlišení, odkud hodnota přišla (`Provenance.source`: `ORIGINAL_QUERY`, `CURRENT_USER_TURN`, `CONVERSATION`, `PROFILE`, `DURABLE_PREFERENCE`, `SYSTEM_POLICY`) — a tedy i `explicitAnyTargets()` a `profileSuppressionTargets()`, které brání profilu přebít to, co uživatel právě řekl.

### 7.3 Nový tvar výsledku

`search_catalog` vrací vždy produkty **plus** stav filtrů. Návrh tvaru:

```json
{
  "products": [ ... ],
  "appliedFilters": {
    "shipsTo":   { "value": "CZ", "source": "PROFILE" },
    "priceTier": { "value": "MID", "source": "CURRENT_USER_TURN" }
  },
  "unsetFilters": ["SIZE", "COLOR", "CONDITION"],
  "resultCount": 47
}
```

`unsetFilters` jsou `UserProductSearchQuestionTarget` hodnoty odvozené z `plan.missingTargets()`. `source` je `Provenance.source` — model tak pozná rozdíl mezi „uživatel právě řekl, že chce mid-range" a „vzali jsme to z profilu", a může to druhé nabídnout k potvrzení.

Rozhodovací pravidlo se přesouvá do system promptu, ne do kódu. Zhruba: *čím víc filtrů je vyplněných, tím relevantnější výsledky; když `unsetFilters` obsahuje dimenzi, která je pro tento typ produktu podstatná a výsledků je mnoho, zeptej se na ni před ukázáním výsledků; jinak ukaž výsledky a nabídni zúžení.*

Model tak u „hledám běžecké boty" pozná, že velikost je podstatná, a zeptá se — u „hledám dárkovou svíčku" ne. To je přesně rozhodnutí, které dnes dělá tabulka a dělá ho špatně, protože nezná typ produktu.

### 7.4 Důsledek pro `AgentProductSearchQualificationService`

Metoda `qualify()` už nikdy nevrací „not ready" jako terminální stav. Zjednoduší se na: vezmi konverzaci a profil → vrať plán s filtry a jejich provenance. Kontrola `if (!qualification.ready())` v `SearchCatalogAgentTool` mizí, stejně jako `requireRequestedFiltersAuthorized` (model teď filtry navrhovat smí, protože se stejně validují proti povoleným hodnotám ve schématu).

---

## 8. Plán implementace

Každá fáze je samostatně nasaditelná a testovatelná.

**Fáze 1 — kontext a prompt** (nízké riziko, okamžitý efekt)
- `AgentContextAssembler`: tool results v historii (§4.2)
- system prompt na ~25 řádků (§4.1)
- zrušit `WAITING_FOR_USER` (§4.3)

**Fáze 2 — zrušení regexových bran** (jádro)
- smazat `AgentReadIntentResolver`, `AgentProductClarificationService`, `AgentProductClarificationContextService`
- vyčistit `AgentRunCoordinator`: řádky 269–300 (read intent + clarification), 305–308 (forsovaný `tool_choice`), 603–615 (`requiresComparisonArtifact`), 324–344 (mutation corrections)
- `AgentToolAuthorizationPolicy` → ~60 řádků, jen risk-class gate
- odstranit hardcoded stringy (§ E) — model generuje vše

**Fáze 3 — referenční integrita**
- `ReferenceIntegrityPolicy` (§5.1)
- smazat `AgentMutationTargetPolicy`, `AgentMutationTargetTextSupport`

**Fáze 3b — qualification jako poradce** (§7)
- smazat `AgentProductSearchQualificationContinuationPolicy`
- `SearchCatalogAgentTool`: odstranit `qualificationRequired` větev a `requireRequestedFiltersAuthorized`
- nový tvar výsledku s `appliedFilters` / `unsetFilters` (§7.3)
- `qualify()` už nevrací terminální „not ready" (§7.4)
- odstranit `qualificationId` / `qualificationUpdatedAt` ze schématu toolu
- doplnit rozhodovací pravidlo o filtrech do system promptu

**Fáze 4 — checkout gate**
- approve flow přes `AgentUserAction` (§5.3)
- frontend: potvrzovací karta

**Fáze 5 — Spring AI**
- `ToolCallingManager.executeToolCalls()` pro conversation history (§6.2)
- `ToolCallback` s reálným `call()`
- `ToolCallingChatOptions` v gateway

---

## 9. Testy

69 testových souborů v `src/test/**/agent/`. Značná část zamyká regexové chování — ty se smažou spolu s produkčním kódem (rozhodnutí D).

Náhrada — behaviorální testy proti fake `AgentModelGateway`:

- **Scénářové testy**: skriptovaný model vrátí sekvenci tool calls, ověřuje se výsledný stav (cart, artefakty, zprávy), ne konkrétní text.
- **Jazyková agnostika**: stejný scénář v EN / CS / DE musí projít identicky. Tohle je nový a klíčový test — dnes by neprošel.
- **Referenční integrita**: model vrátí vymyšlený `offerKey` → očekává se strukturované odmítnutí a možnost recovery.
- **Checkout gate**: model zavolá checkout → nesmí se provést bez user action.
- **Orchestrace** (leasing, lane, cancelace, paralelní tooly): stávající testy zůstávají, tato vrstva se nemění.

---

## 10. Rizika

| Riziko | Zmírnění |
|---|---|
| Model zavolá mutaci, kterou uživatel nechtěl | Akceptováno (rozhodnutí A). Cart je vratný, checkout za gate, vše auditované. |
| Kvalita odpovědí závisí čistě na modelu | Fáze 1 (kontext) jde první a je samostatně měřitelná. Pokud se kvalita nezvedne, je problém v promptu/modelu, ne v architektuře. |
| Delší kontext = vyšší cena a latence | Rozpočet v §4.2 (plné results jen 2 tahy zpět). Měřit přes `AgentMetrics`. |
| Ztráta persistovaných search filtrů | Nehrozí — persistence i UCP mapování zůstávají (§7.2). Mění se jen to, kdo rozhoduje o doptání. |
| Model se přestane ptát na filtry a relevance klesne | Měřit podíl vyhledávání bez filtrů před/po. `unsetFilters` v každém výsledku (§7.3) drží informaci modelu na očích; pokud nestačí, ladí se prompt, ne kód. |
| Velký rozsah změny | 5 samostatně nasaditelných fází; každá dává hodnotu i sama o sobě. |

---

## 11. Co dokument neřeší

- Volba modelu a ladění promptu (patří do samostatného vyhodnocení po fázi 1).
- Frontend nad rámec checkout potvrzovací karty.
- Vazba na UCP plugin refactor — `search_catalog` a `prepare_carts` se ho dotýkají, ale rozhraní toolů se v tomto návrhu nemění.
