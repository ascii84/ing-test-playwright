package pl.ing.automation;

import com.microsoft.playwright.*;
import com.microsoft.playwright.options.AriaRole;
import com.microsoft.playwright.options.WaitUntilState;

import java.time.Duration;
import java.util.regex.Pattern;

/**
 * Page Object for the ING cookie consent banner.
 */
final class CookieConsentPage {

    private static final String URL = "https://www.ing.pl/";

    private static final Pattern CUSTOMIZE =
            Pattern.compile("Dostosuj|Ustawienia", Pattern.CASE_INSENSITIVE);
    private static final Pattern ANALYTICS =
            Pattern.compile("Anality|Statyst", Pattern.CASE_INSENSITIVE);
    private static final Pattern MARKETING =
            Pattern.compile("Marketing", Pattern.CASE_INSENSITIVE);
    private static final Pattern SAVE =
            Pattern.compile("Akceptuj wybrane|Zapisz wybrane|Zapisz ustawienia",
                    Pattern.CASE_INSENSITIVE);
    private static final Pattern CAPTCHA =
            Pattern.compile("Jestem człowiekiem|Dlaczego widzisz tę stronę|Verify you are human",
                    Pattern.CASE_INSENSITIVE);

    private final Page page;

    CookieConsentPage(Page page) {
        this.page = page;
    }

    void open() {
        page.navigate(URL, new Page.NavigateOptions()
                .setWaitUntil(WaitUntilState.DOMCONTENTLOADED));
    }

    void acceptAnalyticsOnly() {
        checkForCaptcha();

        clickText(CUSTOMIZE);
        setCategory(ANALYTICS, true);
        setCategory(MARKETING, false);
        clickText(SAVE);
    }

    void reload() {
        page.reload();
    }

    private void checkForCaptcha() {
        // In local headed mode the user can inspect the page manually.
        if (Boolean.getBoolean("headed")) {
            return;
        }

        String pageText = page.locator("body").innerText();
        if (CAPTCHA.matcher(pageText).find()) {
            throw new AssertionError(
                    "ING displayed an anti-bot verification page instead of the cookie banner. "
                            + "The test does not solve or bypass CAPTCHA. URL: " + page.url());
        }
    }

    private void clickText(Pattern text) {
        Locator control = waitForText(text);
        control.click();
    }

    private void setCategory(Pattern category, boolean enabled) {
        long endTime = System.nanoTime() + Duration.ofSeconds(15).toNanos();

        while (System.nanoTime() < endTime) {
            for (Frame frame : page.frames()) {
                Locator toggle = frame.getByRole(AriaRole.SWITCH,
                        new Frame.GetByRoleOptions().setName(category)).first();

                if (toggle.count() > 0 && toggle.isVisible()) {
                    String currentState = toggle.getAttribute("aria-checked");
                    if (currentState == null) {
                        throw new AssertionError("Cookie switch has no aria-checked attribute: " + category);
                    }

                    if (Boolean.parseBoolean(currentState) != enabled) {
                        toggle.click();
                    }
                    return;
                }

                Locator checkbox = frame.getByRole(AriaRole.CHECKBOX,
                        new Frame.GetByRoleOptions().setName(category)).first();

                if (checkbox.count() > 0 && checkbox.isVisible()) {
                    if (checkbox.isChecked() != enabled) {
                        checkbox.setChecked(enabled);
                    }
                    return;
                }
            }

            page.waitForTimeout(250);
        }

        if (category == ANALYTICS) {
            throw new AssertionError("Could not find the analytics cookie setting.");
        }
    }

    private Locator waitForText(Pattern text) {
        long endTime = System.nanoTime() + Duration.ofSeconds(30).toNanos();

        while (System.nanoTime() < endTime) {
            checkForCaptcha();

            for (Frame frame : page.frames()) {
                Locator locator = frame.getByText(text).first();
                if (locator.count() > 0 && locator.isVisible()) {
                    return locator;
                }
            }

            page.waitForTimeout(250);
        }

        throw new AssertionError("Could not find visible control: " + text);
    }
}
