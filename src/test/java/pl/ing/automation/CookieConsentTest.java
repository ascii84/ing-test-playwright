package pl.ing.automation;

import com.microsoft.playwright.*;
import com.microsoft.playwright.options.AriaRole;
import com.microsoft.playwright.options.Cookie;
import com.microsoft.playwright.options.WaitUntilState;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.*;
import java.util.function.Predicate;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;

/**
 * End-to-end test for analytics cookie consent on ING.
 * Every invocation owns a separate Playwright instance and BrowserContext.
 */
@Execution(ExecutionMode.CONCURRENT)
@TestInstance(TestInstance.Lifecycle.PER_METHOD)
class CookieConsentTest {

    private static final String BASE_URL = "https://www.ing.pl/";
    private static final Pattern CUSTOMIZE = Pattern.compile("dostosuj|ustawienia", Pattern.CASE_INSENSITIVE);
    private static final Pattern ANALYTICS = Pattern.compile("anality|statyst", Pattern.CASE_INSENSITIVE);
    private static final Pattern MARKETING = Pattern.compile("marketing", Pattern.CASE_INSENSITIVE);
    private static final Pattern ACCEPT_SELECTED = Pattern.compile(
            "akceptuj wybrane|zaakceptuj wybrane|zapisz wybrane|zapisz moje wybory|zapisz ustawienia|zapisz wyb[oó]r|akceptuj zaznaczone",
            Pattern.CASE_INSENSITIVE);

    static Stream<BrowserName> browsersToRun() {
        String selected = System.getProperty("browser", "all").trim().toLowerCase(Locale.ROOT);
        if (selected.isBlank() || selected.equals("all")) {
            return Stream.of(BrowserName.values());
        }
        return Stream.of(BrowserName.values())
                .filter(browser -> browser.cliName.equals(selected))
                .findFirst()
                .map(Stream::of)
                .orElseThrow(() -> new IllegalArgumentException(
                        "Unsupported browser: " + selected + ". Use chromium, firefox, webkit or all."));
    }

    @ParameterizedTest(name = "Analytics consent is saved in {0}")
    @MethodSource("browsersToRun")
    void shouldSaveAnalyticsConsentAndAnalyticsCookies(BrowserName browserName) {
        Playwright playwright = null;
        Browser browser = null;
        BrowserContext context = null;
        Page page = null;

        try {
            playwright = Playwright.create();
            browser = browserType(playwright, browserName)
                    .launch(new BrowserType.LaunchOptions().setHeadless(true));
            context = browser.newContext(new Browser.NewContextOptions()
                    .setLocale("pl-PL")
                    .setTimezoneId("Europe/Warsaw")
                    .setViewportSize(1440, 1000));

            assertTrue(context.cookies().isEmpty(), "Fresh BrowserContext should not contain cookies");

            page = context.newPage();
            page.setDefaultTimeout(12_000);
            page.setDefaultNavigationTimeout(60_000);
            page.navigate(BASE_URL, new Page.NavigateOptions()
                    .setWaitUntil(WaitUntilState.DOMCONTENTLOADED)
                    .setTimeout(60_000));

            clickVisibleControl(page, CUSTOMIZE, "Dostosuj");
            setCategoryState(page, ANALYTICS, true, true);
            // Marketing is deliberately left disabled when this optional category is present.
            setCategoryState(page, MARKETING, false, false);
            clickVisibleControl(page, ACCEPT_SELECTED, "Akceptuj wybrane");

            // Revisit the page to prove that the decision persists beyond the current UI state.
            page.reload(new Page.ReloadOptions()
                    .setWaitUntil(WaitUntilState.DOMCONTENTLOADED)
                    .setTimeout(60_000));

            List<Cookie> cookies = waitForCookies(context, page,
                    current -> hasCookie(current, "cookiePolicyGDPR")
                            && hasCookie(current, "cookiePolicyGDPR__details")
                            && current.stream().anyMatch(c -> isAnalyticsCookie(c.name)),
                    Duration.ofSeconds(25));

            Map<String, Cookie> byName = new HashMap<>();
            for (Cookie cookie : cookies) {
                byName.putIfAbsent(cookie.name, cookie);
            }

            Cookie consent = byName.get("cookiePolicyGDPR");
            Cookie details = byName.get("cookiePolicyGDPR__details");
            assertNotNull(consent, "Missing cookiePolicyGDPR");
            assertFalse(consent.value == null || consent.value.isBlank(), "Consent cookie has an empty value");
            assertNotNull(details, "Missing cookiePolicyGDPR__details");
            assertFalse(details.value == null || details.value.isBlank(), "Consent details cookie has an empty value");

            List<String> analyticsCookies = cookies.stream()
                    .map(c -> c.name).filter(CookieConsentTest::isAnalyticsCookie).sorted().toList();
            assertFalse(analyticsCookies.isEmpty(), "No known analytics cookie was stored after analytics consent");

            List<String> marketingCookies = cookies.stream()
                    .map(c -> c.name).filter(CookieConsentTest::isMarketingCookie).sorted().toList();
            assertTrue(marketingCookies.isEmpty(),
                    "Marketing cookies were stored although only analytics consent was selected: " + marketingCookies);

            System.out.printf("[%s] Consent cookies: cookiePolicyGDPR, cookiePolicyGDPR__details%n",
                    browserName.cliName);
            System.out.printf("[%s] Analytics cookies: %s%n", browserName.cliName, analyticsCookies);
            System.out.printf("[%s] PASS%n", browserName.cliName);

        } catch (RuntimeException | AssertionError failure) {
            saveFailureScreenshot(page, browserName);
            throw failure;
        } finally {
            if (context != null) context.close();
            if (browser != null) browser.close();
            if (playwright != null) playwright.close();
        }
    }

    private static BrowserType browserType(Playwright playwright, BrowserName name) {
        return switch (name) {
            case CHROMIUM -> playwright.chromium();
            case FIREFOX -> playwright.firefox();
            case WEBKIT -> playwright.webkit();
        };
    }

    private static void clickVisibleControl(Page page, Pattern name, String description) {
        long deadline = System.nanoTime() + Duration.ofSeconds(30).toNanos();
        while (System.nanoTime() < deadline) {
            for (Frame frame : new ArrayList<>(page.frames())) {
                try {
                    Locator button = frame.getByRole(AriaRole.BUTTON,
                            new Frame.GetByRoleOptions().setName(name)).first();
                    if (button.count() > 0 && button.isVisible()) {
                        button.click();
                        return;
                    }
                    Locator text = frame.getByText(name).first();
                    if (text.count() > 0 && text.isVisible()) {
                        text.click();
                        return;
                    }
                } catch (RuntimeException ignored) {
                    // The consent iframe can reload while the CMP is rendering.
                }
            }
            page.waitForTimeout(250);
        }
        throw new AssertionError("Could not find visible control: " + description);
    }

    private static void setCategoryState(Page page, Pattern category, boolean desired, boolean required) {
        long seconds = required ? 15 : 2;
        long deadline = System.nanoTime() + Duration.ofSeconds(seconds).toNanos();

        while (System.nanoTime() < deadline) {
            for (Frame frame : new ArrayList<>(page.frames())) {
                try {
                    Locator toggle = frame.getByRole(AriaRole.SWITCH,
                            new Frame.GetByRoleOptions().setName(category)).first();
                    if (toggle.count() > 0 && toggle.isVisible()) {
                        setAriaToggleState(toggle, desired);
                        return;
                    }

                    Locator checkbox = frame.getByRole(AriaRole.CHECKBOX,
                            new Frame.GetByRoleOptions().setName(category)).first();
                    if (checkbox.count() > 0 && checkbox.isVisible()) {
                        if (checkbox.isChecked() != desired) checkbox.click();
                        assertEquals(desired, checkbox.isChecked(), "Unexpected checkbox state: " + category);
                        return;
                    }

                    Locator label = frame.locator("label")
                            .filter(new Locator.FilterOptions().setHasText(category)).first();
                    if (label.count() > 0 && label.isVisible()) {
                        Locator input = label.locator("input[type='checkbox']").first();
                        if (input.count() > 0) {
                            if (input.isChecked() != desired) label.click();
                            assertEquals(desired, input.isChecked(), "Unexpected label checkbox state: " + category);
                        } else {
                            // Last-resort support for custom non-semantic toggles.
                            label.click();
                        }
                        return;
                    }
                } catch (RuntimeException ignored) {
                    // Retry until the consent panel is ready.
                }
            }
            page.waitForTimeout(250);
        }

        if (required) throw new AssertionError("Required cookie category not found: " + category);
    }

    private static void setAriaToggleState(Locator toggle, boolean desired) {
        String current = toggle.getAttribute("aria-checked");
        if (current == null) throw new AssertionError("Switch does not expose aria-checked");
        if (Boolean.parseBoolean(current) != desired) toggle.click();
        assertEquals(Boolean.toString(desired), toggle.getAttribute("aria-checked"),
                "Switch did not reach requested state");
    }

    private static List<Cookie> waitForCookies(BrowserContext context, Page page,
                                                Predicate<List<Cookie>> condition, Duration timeout) {
        long deadline = System.nanoTime() + timeout.toNanos();
        while (System.nanoTime() < deadline) {
            List<Cookie> cookies = context.cookies();
            if (condition.test(cookies)) return cookies;
            page.waitForTimeout(300);
        }
        return context.cookies();
    }

    private static boolean hasCookie(List<Cookie> cookies, String name) {
        return cookies.stream().anyMatch(cookie -> cookie.name.equals(name));
    }

    private static boolean isAnalyticsCookie(String name) {
        return name.equals("_ga") || name.equals("_gid") || name.startsWith("_ga_")
                || name.startsWith("s_vi_") || name.equals("s_cc") || name.equals("s_fid")
                || name.equals("Gdyn") || name.startsWith("AMCV_") || name.startsWith("AMCVS_")
                || name.equals("TRADEDOUBLER") || name.equals("TradeDoublerGUID");
    }

    private static boolean isMarketingCookie(String name) {
        return name.equals("cookieSEG") || name.equals("cookieSEG__details") || name.equals("GAD");
    }

    private static void saveFailureScreenshot(Page page, BrowserName browserName) {
        if (page == null) return;
        try {
            Path path = Path.of("target", "screenshots", browserName.cliName + "-failure.png");
            Files.createDirectories(path.getParent());
            page.screenshot(new Page.ScreenshotOptions().setPath(path).setFullPage(true));
            System.err.println("Failure screenshot saved: " + path);
        } catch (Exception screenshotFailure) {
            System.err.println("Could not save screenshot: " + screenshotFailure.getMessage());
        }
    }

    enum BrowserName {
        CHROMIUM("chromium"), FIREFOX("firefox"), WEBKIT("webkit");
        final String cliName;
        BrowserName(String cliName) { this.cliName = cliName; }
        @Override public String toString() { return cliName; }
    }
}
