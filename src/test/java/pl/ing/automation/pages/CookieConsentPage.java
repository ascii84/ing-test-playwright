package pl.ing.automation.pages;

import com.microsoft.playwright.*;
import com.microsoft.playwright.options.AriaRole;
import com.microsoft.playwright.options.WaitUntilState;

import java.time.Duration;
import java.util.regex.Pattern;

/**
 * Page Object for the ING cookie consent banner.
 */
public final class CookieConsentPage {

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

    public CookieConsentPage(Page page) {
        this.page = page;
    }

    public void open() {
        page.navigate(URL, new Page.NavigateOptions()
                .setWaitUntil(WaitUntilState.DOMCONTENTLOADED));
    }

    public void acceptAnalyticsOnly() {
        checkForCaptcha();
        clickButton(CUSTOMIZE);
        setCategory(ANALYTICS, true);
        setCategory(MARKETING, false);
        clickButton(SAVE);
    }

    public void reload() {
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

    private void clickButton(Pattern name) {
        waitForButton(name).click();
    }

    private Locator waitForButton(Pattern name) {
        long endTime = System.nanoTime() + Duration.ofSeconds(30).toNanos();

        while (System.nanoTime() < endTime) {
            checkForCaptcha();

            for (Frame frame : page.frames()) {
                Locator button = frame.getByRole(AriaRole.BUTTON,
                        new Frame.GetByRoleOptions().setName(name)).first();

                if (button.count() > 0 && button.isVisible()) {
                    return button;
                }
            }

            page.waitForTimeout(250);
        }

        throw new AssertionError("Could not find visible button: " + name);
    }

    private void setCategory(Pattern category, boolean enabled) {
        int timeoutSeconds = category == MARKETING ? 3 : 15;
        long endTime = System.nanoTime() + Duration.ofSeconds(timeoutSeconds).toNanos();

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
}
