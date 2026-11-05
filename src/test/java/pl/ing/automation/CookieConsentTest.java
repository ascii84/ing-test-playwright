package pl.ing.automation;

import com.microsoft.playwright.*;
import com.microsoft.playwright.options.Cookie;
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
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;

/**
 * End-to-end test for analytics cookie consent on ING.
 * Every invocation owns a separate Playwright instance and BrowserContext.
 */
@Execution(ExecutionMode.CONCURRENT)
@TestInstance(TestInstance.Lifecycle.PER_METHOD)
class CookieConsentTest {

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
            boolean headed = Boolean.getBoolean("headed");
            browser = browserType(playwright, browserName)
                    .launch(new BrowserType.LaunchOptions()
                            .setHeadless(!headed)
                            .setSlowMo(headed ? 150 : 0));
            context = browser.newContext(new Browser.NewContextOptions()
                    .setLocale("pl-PL")
                    .setTimezoneId("Europe/Warsaw")
                    .setViewportSize(1440, 1000));

            assertTrue(context.cookies().isEmpty(), "Fresh BrowserContext should not contain cookies");

            page = context.newPage();
            page.setDefaultTimeout(12_000);
            page.setDefaultNavigationTimeout(60_000);

            CookieConsentPage cookieConsentPage = new CookieConsentPage(page);
            cookieConsentPage.open();
            cookieConsentPage.configureAnalyticsOnly(headed);
            cookieConsentPage.reload();

            List<Cookie> cookies = waitForCookies(context, page,
                    current -> hasCookie(current, "cookiePolicyGDPR")
                            && hasCookie(current, "cookiePolicyGDPR__details")
                            && current.stream().anyMatch(c -> isAnalyticsCookie(c.name)),
                    Duration.ofSeconds(25));

            Map<String, Cookie> byName = new HashMap<>();
            for (Cookie cookie : cookies) byName.putIfAbsent(cookie.name, cookie);

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
