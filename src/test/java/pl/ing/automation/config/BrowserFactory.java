package pl.ing.automation.config;

import com.microsoft.playwright.Browser;
import com.microsoft.playwright.BrowserType;
import com.microsoft.playwright.Playwright;

public final class BrowserFactory {

    private BrowserFactory() {
    }

    public static Browser launch(Playwright playwright, BrowserName browserName, boolean headed) {
        BrowserType browserType = switch (browserName) {
            case CHROMIUM -> playwright.chromium();
            case FIREFOX -> playwright.firefox();
            case WEBKIT -> playwright.webkit();
        };

        return browserType.launch(new BrowserType.LaunchOptions()
                .setHeadless(!headed)
                .setSlowMo(headed ? 150 : 0));
    }
}
