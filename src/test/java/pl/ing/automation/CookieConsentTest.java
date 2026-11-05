package pl.ing.automation;

import com.microsoft.playwright.*;
import com.microsoft.playwright.options.Cookie;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Locale;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;

@Execution(ExecutionMode.CONCURRENT)
class CookieConsentTest {

    static Stream<BrowserName> browsersToRun() {
        String selected = System.getProperty("browser", "all").toLowerCase(Locale.ROOT);

        if (selected.equals("all")) {
            return Stream.of(BrowserName.values());
        }

        return Stream.of(BrowserName.values())
                .filter(browser -> browser.name().equalsIgnoreCase(selected))
                .findFirst()
                .map(Stream::of)
                .orElseThrow(() -> new IllegalArgumentException("Unknown browser: " + selected));
    }

    @ParameterizedTest(name = "Cookie consent test on {0}")
    @MethodSource("browsersToRun")
    void shouldSaveAnalyticsConsent(BrowserName browserName) {
        Playwright playwright = null;
        Browser browser = null;
        BrowserContext context = null;
        Page page = null;

        try {
            playwright = Playwright.create();

            boolean headed = Boolean.getBoolean("headed");
            browser = getBrowser(playwright, browserName).launch(
                    new BrowserType.LaunchOptions()
                            .setHeadless(!headed)
                            .setSlowMo(headed ? 150 : 0));

            context = browser.newContext(new Browser.NewContextOptions()
                    .setLocale("pl-PL")
                    .setTimezoneId("Europe/Warsaw")
                    .setViewportSize(1440, 1000));

            assertTrue(context.cookies().isEmpty(), "Browser context should start without cookies");

            page = context.newPage();
            page.setDefaultTimeout(15_000);

            CookieConsentPage consentPage = new CookieConsentPage(page);
            consentPage.open();
            consentPage.acceptAnalyticsOnly();
            consentPage.reload();

            List<Cookie> cookies = waitForConsentCookies(context, page);
            List<String> cookieNames = cookies.stream().map(cookie -> cookie.name).toList();

            Cookie consentCookie = findCookie(cookies, "cookiePolicyGDPR");
            Cookie detailsCookie = findCookie(cookies, "cookiePolicyGDPR__details");

            assertNotNull(consentCookie, "Consent cookie was not saved");
            assertFalse(consentCookie.value.isBlank(), "Consent cookie is empty");
            assertNotNull(detailsCookie, "Consent details cookie were not saved");
            assertFalse(detailsCookie.value.isBlank(), "Consent details cookie is empty");

            assertTrue(cookieNames.stream().anyMatch(CookieConsentTest::isAnalyticsCookie),
                    "No analytics cookie was saved");

            assertFalse(cookieNames.stream().anyMatch(CookieConsentTest::isMarketingCookie),
                    "Marketing cookies should not be saved");

            System.out.println("Test passed on " + browserName);

        } catch (RuntimeException | AssertionError error) {
            saveScreenshot(page, browserName);
            throw error;
        } finally {
            if (context != null) context.close();
            if (browser != null) browser.close();
            if (playwright != null) playwright.close();
        }
    }

    private static BrowserType getBrowser(Playwright playwright, BrowserName browserName) {
        return switch (browserName) {
            case CHROMIUM -> playwright.chromium();
            case FIREFOX -> playwright.firefox();
            case WEBKIT -> playwright.webkit();
        };
    }

    private static List<Cookie> waitForConsentCookies(BrowserContext context, Page page) {
        long endTime = System.nanoTime() + Duration.ofSeconds(25).toNanos();
        List<Cookie> cookies = context.cookies();

        while (System.nanoTime() < endTime) {
            boolean consentSaved = hasCookie(cookies, "cookiePolicyGDPR")
                    && hasCookie(cookies, "cookiePolicyGDPR__details");
            boolean analyticsSaved = cookies.stream()
                    .anyMatch(cookie -> isAnalyticsCookie(cookie.name));

            if (consentSaved && analyticsSaved) {
                return cookies;
            }

            page.waitForTimeout(250);
            cookies = context.cookies();
        }

        return cookies;
    }

    private static boolean hasCookie(List<Cookie> cookies, String name) {
        return findCookie(cookies, name) != null;
    }

    private static Cookie findCookie(List<Cookie> cookies, String name) {
        return cookies.stream()
                .filter(cookie -> cookie.name.equals(name))
                .findFirst()
                .orElse(null);
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

    private static void saveScreenshot(Page page, BrowserName browserName) {
        if (page == null) return;

        try {
            Path screenshot = Path.of("target", "screenshots", browserName.name().toLowerCase() + ".png");
            Files.createDirectories(screenshot.getParent());
            page.screenshot(new Page.ScreenshotOptions().setPath(screenshot).setFullPage(true));
        } catch (Exception error) {
            System.err.println("Could not save screenshot: " + error.getMessage());
        }
    }

    enum BrowserName {
        CHROMIUM, FIREFOX, WEBKIT
    }
}
