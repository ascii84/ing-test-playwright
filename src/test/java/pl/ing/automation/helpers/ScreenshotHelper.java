package pl.ing.automation.helpers;

import com.microsoft.playwright.Page;
import pl.ing.automation.config.BrowserName;

import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Pomocnik do zapisywania zrzutów ekranu po nieudanym teście.
 */
public final class ScreenshotHelper {

    private ScreenshotHelper() {
    }

    public static void save(Page page, BrowserName browserName) {
        if (page == null) {
            return;
        }

        try {
            Path screenshot = Path.of(
                    "target",
                    "screenshots",
                    browserName.name().toLowerCase() + ".png"
            );

            Files.createDirectories(screenshot.getParent());
            page.screenshot(new Page.ScreenshotOptions()
                    .setPath(screenshot)
                    .setFullPage(true));

        } catch (Exception error) {
            System.err.println("Nie udało się zapisać zrzutu ekranu: " + error.getMessage());
        }
    }
}
