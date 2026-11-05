package pl.ing.automation.tests;

import com.microsoft.playwright.*;
import com.microsoft.playwright.options.Cookie;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import pl.ing.automation.config.BrowserFactory;
import pl.ing.automation.config.BrowserName;
import pl.ing.automation.helpers.CookieHelper;
import pl.ing.automation.helpers.ScreenshotHelper;
import pl.ing.automation.pages.CookieConsentPage;

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
            browser = BrowserFactory.launch(playwright, browserName, headed);

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

            List<Cookie> cookies = CookieHelper.waitForConsentCookies(context, page);
            List<String> cookieNames = cookies.stream().map(cookie -> cookie.name).toList();

            Cookie consentCookie = CookieHelper.findCookie(cookies, "cookiePolicyGDPR");
            Cookie detailsCookie = CookieHelper.findCookie(cookies, "cookiePolicyGDPR__details");

            assertNotNull(consentCookie, "Consent cookie was not saved");
            assertFalse(consentCookie.value.isBlank(), "Consent cookie is empty");
            assertNotNull(detailsCookie, "Consent details cookie were not saved");
            assertFalse(detailsCookie.value.isBlank(), "Consent details cookie is empty");

            assertTrue(cookieNames.stream().anyMatch(CookieHelper::isAnalyticsCookie),
                    "No analytics cookie was saved");

            assertFalse(cookieNames.stream().anyMatch(CookieHelper::isMarketingCookie),
                    "Marketing cookies should not be saved");

            System.out.println("Test passed on " + browserName);

        } catch (RuntimeException | AssertionError error) {
            ScreenshotHelper.save(page, browserName);
            throw error;
        } finally {
            if (context != null) context.close();
            if (browser != null) browser.close();
            if (playwright != null) playwright.close();
        }
    }
}
