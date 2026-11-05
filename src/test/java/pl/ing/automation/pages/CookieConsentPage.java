package pl.ing.automation.pages;

import com.microsoft.playwright.*;
import com.microsoft.playwright.options.AriaRole;
import com.microsoft.playwright.options.WaitUntilState;

import java.util.ArrayList;
import java.util.List;

/**
 * Obiekt strony reprezentujący baner zgody na pliki cookie ING.
 */
public final class CookieConsentPage {

    private static final String URL = "https://www.ing.pl/";

    private static final String CUSTOMIZE = "Dostosuj";
    private static final String ANALYTICAL = "Analityczne";
    private static final String MARKETING = "Marketingowe";
    private static final String SAVE = "Zaakceptuj zaznaczone";

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
        setCategory(ANALYTICAL, true);
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
                        new Locator.InnerTextOptions().setTimeout(1500)).toLowerCase();

                if (frameText.contains("jestem człowiekiem")
                        || frameText.contains("dlaczego widzisz tę stronę")
                        || frameText.contains("możliwe, że jesteś za granicą")
                        || frameText.contains("why do you see this page")
                        || frameText.contains("i am human")
                        || frameText.contains("i'm human")
                        || frameText.contains("verify you are human")
                        || frameText.contains("hcaptcha")) {
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

    private void clickButton(String name) {
        checkForCaptcha();

        for (Frame frame : page.frames()) {
            Locator button = frame.getByRole(AriaRole.BUTTON,
                    new Frame.GetByRoleOptions().setName(name)).first();

            if (button.count() > 0) {
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

    private void setCategory(String category, boolean enabled) {
        for (Frame frame : page.frames()) {
            Locator toggle = frame.getByRole(AriaRole.SWITCH,
                    new Frame.GetByRoleOptions().setName(category)).first();

            if (toggle.count() > 0) {
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

            if (checkbox.count() > 0) {
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
