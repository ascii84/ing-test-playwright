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

Przed uruchomieniem projektu zainstaluj:

- **Git** – do pobrania repozytorium,
- **Java JDK 21 lub nowszą** – projekt korzysta z Javy 21,
- **Apache Maven 3.9 lub nowszy** – do budowania i uruchamiania testów,
- połączenie z internetem – strona ING oraz pobranie zależności i przeglądarek Playwright.

Nie musisz wcześniej instalować Chrome, Firefox ani Safari. Playwright pobiera własne wersje przeglądarek.

### Instalacja narzędzi

#### macOS (w tym Apple Silicon: M1/M2/M3/M4)

1. Zainstaluj [Homebrew](https://brew.sh/). Oficjalna komenda instalacyjna:

   ```bash
   /bin/bash -c "$(curl -fsSL https://raw.githubusercontent.com/Homebrew/install/HEAD/install.sh)"
   ```

2. Po instalacji Homebrew dodaj je do ścieżki. Dla Apple Silicon (M1/M2/M3/M4):

   ```bash
   echo 'eval "$(/opt/homebrew/bin/brew shellenv)"' >> ~/.zprofile
   eval "$(/opt/homebrew/bin/brew shellenv)"
   ```

   Dla Maców z procesorem Intel Homebrew zwykle znajduje się w `/usr/local`; postępuj zgodnie z instrukcją wyświetloną przez instalator Homebrew.

3. Zainstaluj Git, Java 21 i Maven:

   ```bash
   brew install git
   brew install --cask temurin@21
   brew install maven
   ```

4. Sprawdź instalację:

   ```bash
   git --version
   java -version
   mvn -version
   ```

#### Windows

Zainstaluj Git, JDK 21 (np. Eclipse Temurin) oraz Maven 3.9+:

- [Git for Windows](https://git-scm.com/download/win)
- [Eclipse Temurin JDK 21](https://adoptium.net/temurin/releases/?version=21)
- [Apache Maven](https://maven.apache.org/install.html)

Po instalacji otwórz nowe okno PowerShell i sprawdź:

```powershell
git --version
java -version
mvn -version
```

Jeśli polecenie `mvn` nie jest rozpoznawane, dodaj katalog `bin` instalacji Maven do zmiennej środowiskowej `PATH` i otwórz ponownie terminal.

#### Linux (Ubuntu/Debian)

Zainstaluj wymagane narzędzia:

```bash
sudo apt update
sudo apt install -y git openjdk-21-jdk maven
```

Sprawdź instalację:

```bash
git --version
java -version
mvn -version
```

## Pobranie projektu

Otwórz terminal i przejdź do katalogu, w którym chcesz umieścić projekt. Następnie wykonaj:

```bash
git clone https://github.com/ascii84/ing-test-playwright.git
cd ing-test-playwright
```

**Ważne:** wszystkie poniższe polecenia Maven uruchamiaj z katalogu projektu, w którym znajduje się plik `pom.xml`. Jeśli terminal zgłasza, że nie znaleziono `pom.xml`, najpierw przejdź do katalogu `ing-test-playwright`.

## Uruchomienie lokalne

### Instalacja przeglądarek Playwright

Pobierz binaria Chromium, Firefox i WebKit:

```bash
mvn -B -DskipTests org.codehaus.mojo:exec-maven-plugin:3.5.0:java -Dexec.mainClass=com.microsoft.playwright.CLI -Dexec.args="install"
```

Na Linuxie może być też konieczne doinstalowanie bibliotek systemowych wymaganych przez przeglądarki:

```bash
mvn -B -DskipTests org.codehaus.mojo:exec-maven-plugin:3.5.0:java -Dexec.mainClass=com.microsoft.playwright.CLI -Dexec.args="install-deps"
```

### Uruchomienie testów

Uruchom testy we wszystkich trzech przeglądarkach:

```bash
mvn clean test
```

Domyślnie trzy przeglądarki uruchamiane są równolegle. Każda iteracja testu tworzy własną instancję Playwright, przeglądarkę i czysty `BrowserContext`; cookies nie są współdzielone.

### Uruchomienie lokalne w widocznej przeglądarce

Aby uruchomić pojedynczy test Chromium w normalnym, widocznym oknie przeglądarki:

```bash
mvn -Dbrowser=chromium -Dheaded=true test
```

W trybie `headed` Playwright otwiera przeglądarkę na ekranie i pomija sprawdzanie, czy ING wyświetlił stronę CAPTCHA. Nie zatrzymuje testu w Playwright Inspector. To ustawienie nie omija ani nie rozwiązuje CAPTCHA — jeśli ING pokaże weryfikację, dalsze znalezienie banera cookies może się nie udać. W trybie domyślnym (headless) pozostaje kontrola wykrywająca ekran antybotowy i zgłaszająca czytelny błąd.

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
│       │   ├── CookieConsentPage.java
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
