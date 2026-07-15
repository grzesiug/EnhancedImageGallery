# Handoff: przeglądanie wątków wiadomości AITT w EEG (Enhanced Evidence Gallery)

Notatka dla wątku pracującego nad **EnhancedImageGallery/EEG**. Strona
**AITextTriage (AITT)** jest już gotowa i wystawia dane — brakuje tylko UI w EEG.
Kontekst spisany z sesji AITT (2026-07-15), zweryfikowany w kodzie.

---

## 1. Co AITT już dostarcza (kontrakt gotowy)

Etap J w AITT indeksuje **wątki wiadomości** (SMS/czaty `TSK_MESSAGE`, e-maile
`TSK_EMAIL_MSG`) jako dokumenty logiczne w tym samym indeksie FAISS co pliki.
Odpowiedzi `/search` **oraz** `/categorize` mają teraz na każdym hicie dwa nowe pola:

```
/search  hit:  { file_id, chunk_idx, score, snippet,
                 doc_kind, doc_label, doc_app, doc_participants,
                 doc_msg_count, doc_date_start, doc_date_end }
/categorize hit: { ... margin, score, snippet, <te same pola doc_*> }
```

- `doc_kind`: `""` (zwykły plik), `"thread-chat"` (SMS/komunikator), `"thread-email"`.
- `doc_label`: gotowa etykieta do wyświetlenia, np.
  `"Chat (SMS): +48 501-234-567 <-> 887654321 (12 msgs)"` albo
  `"E-mail thread: Re: rekrutacja EC Tęgoborze (5 msgs)"`.
- **Metadane strukturalne do grupowania** (puste dla zwykłych plików):
  - `doc_app`: `"sms"` | `"whatsapp"` | `"email"` | … (typ/aplikacja),
  - `doc_participants`: lista **znormalizowanych** uczestników
    (numery → 9 ostatnich cyfr, e-mail → lowercase), np. `["501234567","887654321"]`,
  - `doc_msg_count`: liczba wiadomości w wątku,
  - `doc_date_start` / `doc_date_end`: ISO-8601 UTC zakresu czasu wątku (`""` gdy nieznany).
- `snippet`: **query-aware** (okno wokół trafionego zdania; dla wątku — fragment
  transkryptu), już oczyszczony ze znaków sterujących.

Te same pola `doc_*` zwraca też `/document` (§4). Grupowanie w EEG jest więc
możliwe wprost po `doc_app` / `doc_participants` / `doc_date_start` — bez
parsowania `doc_label`.

Dla wątku **`file_id` = obj_id PIERWSZEJ (najniższej) wiadomości wątku** — czyli
**artefakt** `TSK_EMAIL_MSG`/`TSK_MESSAGE`, a NIE `AbstractFile`.

Źródło: `AITextTriage/service/app/main.py` (`_hits_to_json`, `/categorize`),
`service/app/faiss_index.py` (`SearchHit`/`CategoryHit` z `doc_kind`/`doc_label`).

---

## 2. Główny haczyk (to trzeba obsłużyć)

`EnhancedGalleryTopComponent.setSemanticResult(ids, order, snippets, label)`
filtruje wczytane **kafle plików** po `obj_id` plików. Trafienie-wątek ma
`file_id` = **obj_id artefaktu**, którego NIE ma wśród wczytanych `MediaFile`
(EEG ładuje pliki, nie artefakty). Efekt bez zmian: **hity-wątki po prostu
znikają z galerii**.

Trzeba więc zdecydować, jak wpiąć wyniki-artefakty:
- **(A, rekomendacja)** twórz syntetyczny `MediaFile` dla wątku: opakuj **plik
  źródłowy artefaktu** (`artifact.getParent()` / plik nadrzędny — SMS: `mmssms.db`,
  e-mail: `.eml`/mbox) + zapamiętaj `artifactObjId` i `doc_label`. Dzięki temu
  review-state/tagowanie/grupowanie działają bez przebudowy modelu, a dwuklik ma
  do czego się odwołać. Wymaga w `MediaFile` pól `artifactObjId` + `label` i
  gałęzi „kind == thread".
- **(B)** osobny panel/lista wyników-konwersacji obok siatki miniatur. Prostsze
  wizualnie, ale rozjeżdża się z resztą UX (filtry, tagi, grupy).

---

## 3. Proponowane UI (MVP)

1. **Kafel „Konwersacja"** (karta tekstowa zamiast miniatury): ikona dymka czatu,
   tytuł = `doc_label` (uczestnicy/temat), badge z liczbą wiadomości + zakresem dat.
   `MediaType` ma już wartość `DOCUMENT` — dodać rozpoznanie „thread" (po `doc_kind`).
2. **Tooltip + PropertiesPanel** — snippet już płynie (`getSemanticSnippet`,
   tooltip w `ThumbnailGrid.getToolTipText`, czas znikania 10 s już ustawiony).
   Dla wątku pokaż w PropertiesPanel: uczestnicy, aplikacja (SMS/WhatsApp/e-mail),
   liczba wiadomości, zakres dat (parsowalne z `doc_label` lub z artefaktu).
3. **Dwuklik → transkrypt** z podświetleniem trafionego fragmentu (patrz §4).
4. **Filtr / grupa „Messages"** obok istniejących typów. EEG ma już mechanizm
   grupowania (np. „by MIME") — dołożyć grupowanie wątków po:
   - **konwersacji** (sam wątek = grupa; naturalne, po `file_id` wątku),
   - **uczestnikach / odbiorcach** (nadawcy+odbiorcy),
   - **aplikacji** (SMS / WhatsApp / e-mail),
   - **dacie** (zakres czasu wątku).
   ⚠️ Grupowanie po **uczestnikach/odbiorcach/dacie wymaga metadanych
   strukturalnych** — patrz §7. `doc_label` to tylko string do wyświetlenia
   (parsowanie go pod grupowanie jest kruche i NIEZALECANE).

Kod-punkty zaczepienia w EEG:
- `ui/EnhancedGalleryTopComponent.java`: `setSemanticResult(...)`,
  `getSemanticSnippet(long)`, mapa `semanticSnippets`, rekord hita `fileId()/snippet()`
  (dołożyć `docKind()/docLabel()` przy parsowaniu odpowiedzi HTTP).
- `search/AiTextSearchService.java`: parsowanie odpowiedzi `/search` — dodać
  odczyt `doc_kind`/`doc_label`.
- `ui/ThumbnailGrid.java`: render kafla (gałąź „thread"), `getToolTipText`.
- `ui/PropertiesPanel.java`: wiersze metadanych wątku.
- `datamodel/MediaFile.java`: pola `artifactObjId`, `docLabel`, `docKind`.

---

## 4. Po stronie AITT — ZROBIONE ✅ (endpoint `/document`)

Endpoint **`/document` jest już gotowy** (AITT commit f9e06a4):
```
GET /document?index_dir=<...>&file_id=<docId>
  -> { file_id, doc_kind, doc_label, text }        (404 gdy brak w indeksie)
```
`text` to sklejony transkrypt (chunki złożone po `char_start`, nakładki przycięte,
bez dublowania). Zweryfikowane e2e na `emailTest`: dla trafienia `thread-email`
zwraca pełny transkrypt (21 wiadomości) w formacie:
```
E-mail thread: FW: <temat>
--- [YYYY-MM-DD HH:MM] From: <nadawca>; To: <odbiorcy>; ---
<treść wiadomości>
--- [YYYY-MM-DD HH:MM] From: ... ---
...
```
Czaty (`thread-chat`): `[YYYY-MM-DD HH:MM] [in]/[out] <nadawca>: <treść>` (patrz
`AITextTriage/.../MessageThreadIndexer.buildTranscript`). Format jest stały i
łatwy do parsowania na bąbelki. Do podświetlenia trafionej wiadomości można użyć
`snippet` z hita (query-aware) — znajdź go w transkrypcie i podświetl.

Czyli po stronie EEG zostaje TYLKO UI — dane i transkrypt są gotowe.

---

## 5. Otwarte decyzje

- Kafel-karta (A) vs osobny panel (B) — rekomendacja: A.
- Podgląd transkryptu: własne okno (bąbelki L/P jak w telefonie) czy prosty
  `JTextArea` z podświetleniem — MVP: `JTextArea`, bąbelki później.
- Skok do artefaktu w Autopsy (widok Communications) — miły dodatek, nie MVP.

## 7. Metadane strukturalne wątku (do grupowania) — ZROBIONE ✅

AITT wystawia komplet pól strukturalnych na każdym hicie i w `/document`
(AITT commit 7c3f193): `doc_app`, `doc_participants` (znormalizowane),
`doc_msg_count`, `doc_date_start`, `doc_date_end` — patrz §1. Liczone w
`MessageThreadIndexer` (agregat po WSZYSTKICH wiadomościach wątku; EEG nie
policzyłby ich sam, bo widzi tylko artefakt pierwszej wiadomości).

Grupowanie w EEG jest więc możliwe wprost:
- **po aplikacji** → `doc_app`,
- **po uczestnikach/odbiorcach** → zbiór `doc_participants` (klucz grupy = np.
  posortowany `doc_participants` złączony),
- **po dacie** → `doc_date_start` (np. bucket dzienny/miesięczny),
- **po konwersacji** → `file_id`.

Uwaga: pola wypełnia dopiero ingest ≥ AITT 1.7 (re-ingest zaktualizuje `meta.json`;
stare wpisy `[kind,label]` wczytują się wstecznie jako `{kind,label}` bez metadanych).

## 6. Kolejność

1. ~~AITT: dopisać `/document`~~ — **ZROBIONE** (§4).
2. ~~AITT: strukturalne metadane wątku~~ — **ZROBIONE** (§7).
3. EEG: klient czyta `doc_*`; `MediaFile` dla wątku (A); kafel-karta.
4. EEG: PropertiesPanel + tooltip dla wątku.
5. EEG: dwuklik → `/document` → transkrypt z podświetleniem.
6. EEG: filtr/grupa „Messages" (po app / uczestnikach / dacie / konwersacji — §7).

Weryfikacja e2e: sprawa `emailTest` ma 8643 wątki e-mail zaindeksowane —
`/search` zwraca hity `doc_kind="thread-email"` z `doc_label`; wystarczy je pokazać.

---

## 8. Zachowanie bez modułu AITT / bez ingestu (graceful degradation)

Stan OBECNY w EEG (`EnhancedGalleryTopComponent.runSemanticSearch` +
`AiTextSearchService`) — działa poprawnie, karty konwersacji muszą go zachować:

- **AITT niezainstalowany** → `ensureRunning()` → `findServiceDir()` rzuca
  „AI Text Triage service not found. Install it…" → łapane jako fatal →
  `showAiTextUnavailableDialog` = czytelny dialog „Text search jest zasilane
  modułem AI Text Triage — zainstaluj .nbm i uruchom ingest". ✓
- **Serwis zainstalowany, ale zatrzymany** (serwis AITT żyje tylko podczas
  ingestu) → `ensureRunning()` sam go startuje i czeka na `/health`. ✓
- **Embedder-zaślepka** (brak wag modelu) → 503 → `EmbedderUnavailableException`
  → dialog „embedder has no model weights (stub)". ✓
- **Indeks zbudowany innym modelem** → 409 → `IndexModelMismatchException` →
  dialog „re-run AI Text Triage ingest". ✓
- **Sprawa NIE przeszła ingestu AITT** (brak `ModuleOutput/AITextTriage`):
  `currentTextIndexDir()` i tak zwraca ścieżkę, serwis otwiera **pusty** indeks
  i zwraca `[]` → EEG pokazuje **„No matches for: q"**. Brak crasha, ale
  **mylące** (nie odróżnia „brak trafień" od „nigdy nie indeksowano").
  🔧 **Zalecane (drobne, EEG)**: przed wyszukiwaniem sprawdzić istnienie indeksu
  (`<idxDir>/text_embeddings.faiss` lub `meta.json`); gdy brak → pokazać
  „Ta sprawa nie została zindeksowana przez AI Text Triage — uruchom jego ingest",
  zamiast „No matches". To samo dotyczy kart konwersacji (bez indeksu — po prostu
  brak kart, bez błędu).

Zasada dla kart konwersacji: **każda ścieżka do AITT musi być owinięta w tę samą
obsługę** (moduł może nie istnieć / serwis może nie odpowiadać) — nigdy nie
zakładać, że `/search`//`document` są dostępne.
