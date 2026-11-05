package pl.ing.automation.pages;

import com.microsoft.playwright.*;
import com.microsoft.playwright.options.AriaRole;
import com.microsoft.playwright.options.WaitForSelectorState;
import com.microsoft.playwright.options.WaitUntilState;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

/**
 * Obiekt strony reprezentujący baner zgody na pliki cookie ING.
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
            Pattern.compile("Jestem\\s+cz[łl]owiekiem|Dlaczego\\s+widzisz\\s+t[eę]\\s+stron[eę]|"
                    + "Możliwe, że jesteś za granicą|Why do you see this page|"
                    + "I am human|I'm human|Verify you are human|hcaptcha",
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
        // W lokalnym trybie z widoczną przeglądarką użytkownik może sam sprawdzić stronę.
        if (Boolean.getBoolean("headed")) {
            return;
        }

        for (Frame frame : page.frames()) {
            String frameUrl = frame.url();

            if (frameUrl != null && frameUrl.toLowerCase().contains("captcha")) {
                throw new AssertionError(
                        "ING loaded a CAPTCHA frame instead of the cookie banner. "
                                + "The test does not solve or bypass CAPTCHA. URL: " + frameUrl);
            }

            try {
                String frameText = frame.locator("body").innerText(
                        new Locator.InnerTextOptions().setTimeout(1500));

                if (CAPTCHA.matcher(frameText).find()) {
                    throw new AssertionError(
                            "ING displayed an anti-bot verification page instead of the cookie banner. "
                                    + "The test does not solve or bypass CAPTCHA. "
                                    + "Frame URL: " + frameUrl
                                    + ", page URL: " + page.url()
                                    + ", page text: " + shorten(frameText));
                }
            } catch (TimeoutError ignored) {
                // Pusta lub wciąż ładująca się ramka może jeszcze nie mieć treści.
            }
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

        checkForCaptcha();
        throw new AssertionError("Could not find button: " + name
                + ". Page URL: " + page.url()
                + ". Page title: " + page.title()
                + ". Frame URLs: " + frameUrls()
                + ". Page text: " + pageText());
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

        throw new AssertionError("Could not find cookie setting: " + category
                + ". Page URL: " + page.url()
                + ". Frame URLs: " + frameUrls()
                + ". Page text: " + pageText());
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

    private String pageText() {
        List<String> texts = new ArrayList<>();
        for (Frame frame : page.frames()) {
            try {
                String value = frame.locator("body").innerText(
                        new Locator.InnerTextOptions().setTimeout(1000)).replaceAll("\\s+", " ").trim();
                if (!value.isBlank()) {
                    texts.add(shorten(value));
                }
            } catch (RuntimeException ignored) {
                // Zachowaj diagnostykę z tych ramek, do których można uzyskać dostęp.
            }
        }
        return texts.toString();
    }

    private String frameUrls() {
        return page.frames().stream().map(Frame::url).toList().toString();
    }

    private static String shorten(String value) {
        String normalized = value.replaceAll("\\s+", " ").trim();
        return normalized.length() > 400 ? normalized.substring(0, 400) + "..." : normalized;
    }
}
