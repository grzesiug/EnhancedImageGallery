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
/search  hit:  { file_id, chunk_idx, score, snippet, doc_kind, doc_label }
/categorize hit:{ file_id, chunk_idx, margin, score, snippet, doc_kind, doc_label }
```

- `doc_kind`: `""` (zwykły plik), `"thread-chat"` (SMS/komunikator), `"thread-email"`.
- `doc_label`: gotowa etykieta do wyświetlenia, np.
  `"Chat (SMS): +48 501-234-567 <-> 887654321 (12 msgs)"` albo
  `"E-mail thread: Re: rekrutacja EC Tęgoborze (5 msgs)"`.
- `snippet`: **query-aware** (okno wokół trafionego zdania; dla wątku — fragment
  transkryptu), już oczyszczony ze znaków sterujących.

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

## 7. Metadane strukturalne wątku (potrzebne do grupowania) — DO USTALENIA

Dziś kontrakt daje tylko `doc_label` (string). Do grupowania po uczestnikach /
odbiorcach / dacie / aplikacji potrzeba pól **strukturalnych**. EEG nie policzy
ich sam: widzi tylko artefakt PIERWSZEJ wiadomości, a uczestnicy/zakres dat
wątku to agregat po WSZYSTKICH wiadomościach — a tę agregację robi już AITT
(`MessageThreadIndexer`: normalizacja numerów do 9 cyfr, zbiór uczestników,
`app`, liczba wiadomości, epoch min/max).

**Rekomendacja:** AITT wystawia strukturę wątku (obok `doc_kind`/`doc_label`),
zwracaną w hitach `/search`//`categorize` i w `/document`:
```
thread: {
  app: "sms" | "whatsapp" | "email" | ...,
  participants: ["+48501234567", "887654321", ...],   # znormalizowane
  message_count: 21,
  date_start: "2026-04-24T08:26:00Z",
  date_end:   "2026-05-02T11:13:00Z"
}
```
Wtedy grupowanie w EEG jest trywialne (grupuj po `app` / po zbiorze
`participants` / po `date_start`), bez parsowania `doc_label`.

Status: **NIE zrobione** — to kolejna zmiana formatu indeksu AITT (dodatkowy
re-ingest, by wypełnić pola). Wątek AITT może to dołożyć na życzenie; do czasu
tego EEG może zacząć od grupowania „po konwersacji" (samo `file_id`) i „po app"
(z prefiksu `doc_label`), które nie wymagają nowych pól.

## 6. Kolejność

1. ~~AITT: dopisać `/document`~~ — **ZROBIONE** (§4).
2. AITT (opcjonalnie, dla pełnego grupowania): strukturalne metadane wątku (§7).
3. EEG: klient czyta `doc_kind/doc_label`; `MediaFile` dla wątku (A); kafel-karta.
4. EEG: PropertiesPanel + tooltip dla wątku.
5. EEG: dwuklik → `/document` → transkrypt z podświetleniem.
6. EEG: filtr/grupa „Messages" (konwersacja/app od razu; uczestnicy/data po §7).

Weryfikacja e2e: sprawa `emailTest` ma 8643 wątki e-mail zaindeksowane —
`/search` zwraca hity `doc_kind="thread-email"` z `doc_label`; wystarczy je pokazać.
