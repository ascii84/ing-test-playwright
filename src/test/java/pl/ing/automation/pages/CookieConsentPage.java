package pl.ing.automation.pages;

import com.microsoft.playwright.*;
import com.microsoft.playwright.options.AriaRole;
import com.microsoft.playwright.options.WaitForSelectorState;
import com.microsoft.playwright.options.WaitUntilState;

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
            Pattern.compile("Zaakceptuj zaznaczone",
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
        checkForCaptcha();

        for (Frame frame : page.frames()) {
            Locator button = findVisible(frame, AriaRole.BUTTON, name);
            if (button != null) {
                button.click();
                return;
            }
        }

        throw new AssertionError("Could not find button: " + name);
    }

    private void setCategory(Pattern category, boolean enabled) {
        for (Frame frame : page.frames()) {
            Locator toggle = findVisible(frame, AriaRole.SWITCH, category);

            if (toggle != null) {
                String currentState = toggle.getAttribute("aria-checked");
                if (currentState == null) {
                    throw new AssertionError("Cookie switch has no aria-checked attribute: " + category);
                }
                if (Boolean.parseBoolean(currentState) != enabled) {
                    toggle.click();
                }
                return;
            }

            Locator checkbox = findVisible(frame, AriaRole.CHECKBOX, category);
            if (checkbox != null) {
                if (checkbox.isChecked() != enabled) {
                    checkbox.setChecked(enabled);
                }
                return;
            }
        }

        throw new AssertionError("Could not find cookie setting: " + category);
    }

    private Locator findVisible(Frame frame, AriaRole role, Pattern name) {
        Locator locator = frame.getByRole(role,
                new Frame.GetByRoleOptions().setName(name)).first();

        try {
            locator.waitFor(new Locator.WaitForOptions()
                    .setState(WaitForSelectorState.VISIBLE)
                    .setTimeout(3000));
            return locator;
        } catch (TimeoutError ignored) {
            return null;
        }
    }
}
