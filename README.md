# ING – test zgody na cookies (Java + Playwright)

Projekt automatyzuje scenariusz rekrutacyjny sprawdzający zapis zgody na cookies analityczne na stronie [https://www.ing.pl](https://www.ing.pl).

## Zakres testu

1. Otwiera stronę ING w nowym, odizolowanym kontekście przeglądarki.
2. W banerze cookies wybiera **Dostosuj**.
3. Włącza kategorię cookies analitycznych.
4. Pozostawia kategorię marketingową wyłączoną.
5. Zapisuje wybór przyciskiem **Akceptuj wybrane** (uwzględnione są też typowe warianty tekstu).
6. Odświeża stronę, aby sprawdzić trwałość zapisanej decyzji.
7. Odczytuje cookies z kontekstu Playwrighta i sprawdza:
   - `cookiePolicyGDPR` – cookie decyzji dotyczącej zgód,
   - `cookiePolicyGDPR__details` – cookie szczegółów decyzji,
   - co najmniej jedno znane cookie analityczne, np. `_ga`, `_gid`, `_ga_*`, `s_vi_*`, `s_cc`, `s_fid`, `Gdyn`, `AMCV_*` lub `AMCVS_*`,
   - brak znanych cookies marketingowych `cookieSEG`, `cookieSEG__details` i `GAD`.

Nazwy cookies należy okresowo weryfikować z aktualną [Polityką plików cookie ING](https://www.ing.pl/indywidualni/tabele-i-regulaminy/regulacje/polityka-plikow-cookie), ponieważ dostawca CMP lub narzędzia analityczne mogą się zmienić.

## Wymagania

- Java 21+
- Maven 3.9+
- Połączenie z internetem (strona ING i pobranie binariów przeglądarek Playwright)

## Uruchomienie lokalne

W katalogu projektu wykonaj:

```bash
# Instalacja przeglądarek Playwright
mvn -B -DskipTests exec:java \
  -Dexec.mainClass=com.microsoft.playwright.CLI \
  -Dexec.args="install"

# Uruchomienie testu w Chromium, Firefox i WebKit
mvn clean test
```

Domyślnie trzy przeglądarki uruchamiane są równolegle. Każda iteracja testu tworzy własną instancję Playwright, przeglądarkę i czysty `BrowserContext`; cookies nie są współdzielone.

### Ręczne przejście weryfikacji ING (widoczna przeglądarka)

Jeżeli ING wyświetla hCaptcha, możesz uruchomić pojedynczy test Chromium lokalnie w trybie headed. Playwright pokaże okno przeglądarki i zatrzyma wykonanie w Playwright Inspector. Wykonaj weryfikację samodzielnie w oknie przeglądarki, a następnie kliknij **Resume** w Inspectorze. Test sprawdzi, czy ekran ochronny zniknął i będzie kontynuował scenariusz cookies.

Najpierw pobierz Chromium:

```bash
mvn -B -DskipTests org.codehaus.mojo:exec-maven-plugin:3.5.0:java \
  -Dexec.mainClass=com.microsoft.playwright.CLI \
  -Dexec.args="install chromium"
```

Następnie uruchom widoczny test:

```bash
mvn -Dbrowser=chromium -Dheaded=true -DmanualVerification=true test
```

Ten tryb wymaga komputera z interfejsem graficznym. Nie uruchamiaj go na GitHub-hosted runnerze. Test nie automatyzuje rozwiązywania CAPTCHA — człowiek wykonuje tę czynność, a Playwright jedynie wznawia scenariusz po ręcznym potwierdzeniu.

### Uruchomienie tylko w jednej przeglądarce

```bash
mvn test -Dbrowser=chromium
mvn test -Dbrowser=firefox
mvn test -Dbrowser=webkit
```

Dozwolone wartości właściwości `browser`: `all` (domyślnie), `chromium`, `firefox`, `webkit`.

## Pipeline – GitHub Actions

Workflow znajduje się w `.github/workflows/cross-browser.yml`. Najpierw job `prepare` pobiera zależności Maven i Playwright CLI jeden raz, aby uniknąć równoczesnego odpytywania Maven Central przez trzy joby (w poprzednim uruchomieniu Maven Central zwrócił HTTP 429). Następnie macierz z trzema wartościami `chromium`, `firefox`, `webkit` uruchamia osobny job dla każdej przeglądarki. `max-parallel: 3` pozwala wykonywać je jednocześnie, a `fail-fast: false` nie przerywa pozostałych jobów po błędzie jednej przeglądarki. Każdy job instaluje tylko biblioteki systemowe i binaria wybranej przeglądarki (np. job Chromium nie pobiera ciężkich zależności WebKit/GStreamer). Runner jest przypięty do `ubuntu-24.04`, a job E2E ma limit 20 minut. CLI Playwright jest wywoływane pełną współrzędną Maven pluginu, bez dynamicznego wyszukiwania prefiksu `exec`.

Workflow uruchamia się po `push`, `pull_request` oraz ręcznie przez `workflow_dispatch`. Raporty Surefire i screenshoty błędów są publikowane jako artefakty. Przed instalacją binariów workflow wykonuje osobny krok `install-deps`, który instaluje systemowe biblioteki dla wszystkich silników Playwright. Następnie pobierane są przeglądarki. Są to kroki zgodne z oficjalną instrukcją Playwright Java.

## Struktura projektu

```text
.
├── .github/
│   └── workflows/
│       └── cross-browser.yml
├── src/
│   └── test/
│       ├── java/pl/ing/automation/
│       │   └── CookieConsentTest.java
│       └── resources/
│           └── junit-platform.properties
├── .gitignore
├── pom.xml
└── README.md
```

## Powtarzalność i diagnostyka

- Każdy test zaczyna się w nowym `BrowserContext`, bez cookies z poprzedniego uruchomienia.
- Jawnie ustawione są locale `pl-PL`, strefa `Europe/Warsaw`, viewport i timeouty.
- Test nie używa stałych opóźnień do synchronizacji; ponawia wyszukiwanie elementów i oczekuje na warunek pojawienia się cookies.
- Maksymalna równoległość lokalna wynosi trzy testy (JUnit 5).
- Przy błędzie zapisywany jest screenshot do `target/screenshots/`.

## Ograniczenie bezpieczeństwa ING (hCaptcha)

ING może pokazać stronę ochronną „Dlaczego widzisz tę stronę?” z weryfikacją „Jestem człowiekiem” zamiast właściwej strony. W szczególności publiczne adresy IP runnerów CI mogą zostać potraktowane jako nietypowe źródło ruchu. W takim przypadku test kończy się czytelnym błędem i zapisuje screenshot. Test celowo nie rozwiązuje ani nie obchodzi CAPTCHA.

Aby uruchomić pełny scenariusz E2E, użyj sieci/runnera dopuszczonego przez ING (np. self-hosted GitHub Actions runner w zaufanej sieci) albo uzgodnionego środowiska testowego. W workflow można ustawić zmienną repozytorium `ING_RUNNER=self-hosted`; domyślnie używany jest `ubuntu-24.04`. Dla prawdziwej równoległości na self-hosted potrzebne są trzy dostępne sloty/instancje runnera.

## Ważna uwaga

To test E2E publicznej strony ING, a nie kontrolowanej aplikacji testowej. Wynik zależy również od dostępności serwisu, sieci, skryptów zewnętrznych i aktualnej implementacji banera zgód. Jeżeli ING zmieni dostawcę analityki albo nazwy cookies, selektory i asercje wymagają aktualizacji.

Na Ubuntu/Linux lokalnie zainstaluj zależności systemowe i przeglądarki:

```bash
mvn -B -DskipTests exec:java -Dexec.mainClass=com.microsoft.playwright.CLI -Dexec.args="install-deps"
mvn -B -DskipTests exec:java -Dexec.mainClass=com.microsoft.playwright.CLI -Dexec.args="install"
```
