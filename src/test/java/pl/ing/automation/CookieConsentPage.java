package pl.ing.automation;

import com.microsoft.playwright.*;
import com.microsoft.playwright.options.AriaRole;
import com.microsoft.playwright.options.WaitUntilState;

import java.time.Duration;
import java.util.*;
import java.util.regex.Pattern;

/**
 * Page Object for ING's cookie-consent flow.
 * Encapsulates navigation, security interstitial handling and consent controls.
 */
final class CookieConsentPage {
    private static final String BASE_URL = "https://www.ing.pl/";
    private static final Pattern CUSTOMIZE = Pattern.compile("dostosuj|ustawienia", Pattern.CASE_INSENSITIVE);
    private static final Pattern ANALYTICS = Pattern.compile("anality|statyst", Pattern.CASE_INSENSITIVE);
    private static final Pattern MARKETING = Pattern.compile("marketing", Pattern.CASE_INSENSITIVE);
    private static final Pattern SECURITY_CHALLENGE = Pattern.compile(
            "dlaczego widzisz t[eę] stron[eę]|jestem cz[łl]owiekiem|i am human|verify you are human",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern ACCEPT_SELECTED = Pattern.compile(
            "akceptuj wybrane|zaakceptuj wybrane|zapisz wybrane|zapisz moje wybory|zapisz ustawienia|zapisz wyb[oó]r|akceptuj zaznaczone",
            Pattern.CASE_INSENSITIVE);

    private final Page page;

    CookieConsentPage(Page page) {
        this.page = Objects.requireNonNull(page);
    }

    void open() {
        page.navigate(BASE_URL, new Page.NavigateOptions()
                .setWaitUntil(WaitUntilState.DOMCONTENTLOADED)
                .setTimeout(60_000));
    }

    void configureAnalyticsOnly() {
        handleSecurityChallenge();
        clickVisibleControl(CUSTOMIZE, "Dostosuj");
        setCategoryState(ANALYTICS, true, true);
        // Marketing is deliberately left disabled when this optional category is present.
        setCategoryState(MARKETING, false, false);
        clickVisibleControl(ACCEPT_SELECTED, "Akceptuj wybrane");
    }

    void reload() {
        page.reload(new Page.ReloadOptions()
                .setWaitUntil(WaitUntilState.DOMCONTENTLOADED)
                .setTimeout(60_000));
    }

    private boolean isSecurityChallengeVisible() {
        String bodyText = "";
        try {
            bodyText = page.locator("body").innerText();
        } catch (RuntimeException ignored) {
            // Page may still be transitioning; frame checks below are also useful.
        }

        boolean challengeText = SECURITY_CHALLENGE.matcher(bodyText).find();
        boolean challengeFrame = page.frames().stream()
                .map(Frame::url)
                .filter(Objects::nonNull)
                .map(url -> url.toLowerCase(Locale.ROOT))
                .anyMatch(url -> url.contains("hcaptcha.com") || url.contains("captcha"));
        return challengeText || challengeFrame;
    }

    private void handleSecurityChallenge() {
        // In headed local runs the user can inspect the page directly; do not pause in Playwright Inspector.
        // Keep explicit anti-bot detection for headless CI, where the test cannot proceed without intervention.
        if (Boolean.getBoolean("headed") || !isSecurityChallengeVisible()) return;

        throw new AssertionError(
                "ING displayed an anti-bot verification page (hCaptcha / 'Jestem człowiekiem') "
                + "instead of the cookie consent banner. For a local manual run, use "
                + "-Dheaded=true -DmanualVerification=true. The test does not solve or bypass CAPTCHA. "
                + "Current URL: " + page.url());
    }

    private void clickVisibleControl(Pattern name, String description) {
        long deadline = System.nanoTime() + Duration.ofSeconds(30).toNanos();
        while (System.nanoTime() < deadline) {
            handleSecurityChallenge();

            for (Frame frame : new ArrayList<>(page.frames())) {
                try {
                    Locator button = frame.getByRole(AriaRole.BUTTON,
                            new Frame.GetByRoleOptions().setName(name)).first();
                    if (button.count() > 0 && button.isVisible()) {
                        button.click();
                        return;
                    }

                    Locator link = frame.getByRole(AriaRole.LINK,
                            new Frame.GetByRoleOptions().setName(name)).first();
                    if (link.count() > 0 && link.isVisible()) {
                        link.click();
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

        String title;
        String bodyPreview;
        try {
            title = page.title();
        } catch (RuntimeException ignored) {
            title = "<unavailable>";
        }
        try {
            bodyPreview = page.locator("body").innerText().replaceAll("\\s+", " ").trim();
            if (bodyPreview.length() > 500) bodyPreview = bodyPreview.substring(0, 500) + "...";
        } catch (RuntimeException ignored) {
            bodyPreview = "<unavailable>";
        }
        String frameUrls = page.frames().stream().map(Frame::url).toList().toString();

        throw new AssertionError("Could not find visible control: " + description
                + ". URL=" + page.url()
                + ", title=" + title
                + ", visible page text=" + bodyPreview
                + ", frames=" + frameUrls);
    }

    private void setCategoryState(Pattern category, boolean desired, boolean required) {
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
                        if (checkbox.isChecked() != desired) {
                            throw new AssertionError("Unexpected checkbox state: " + category);
                        }
                        return;
                    }

                    Locator label = frame.locator("label")
                            .filter(new Locator.FilterOptions().setHasText(category)).first();
                    if (label.count() > 0 && label.isVisible()) {
                        Locator input = label.locator("input[type='checkbox']").first();
                        if (input.count() > 0) {
                            if (input.isChecked() != desired) label.click();
                            if (input.isChecked() != desired) {
                                throw new AssertionError("Unexpected label checkbox state: " + category);
                            }
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
        if (!Boolean.toString(desired).equals(toggle.getAttribute("aria-checked"))) {
            throw new AssertionError("Switch did not reach requested state");
        }
    }
}
