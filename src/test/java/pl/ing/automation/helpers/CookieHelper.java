package pl.ing.automation.helpers;

import com.microsoft.playwright.BrowserContext;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.options.Cookie;

import java.time.Duration;
import java.util.List;

public final class CookieHelper {

    private CookieHelper() {
    }

    public static List<Cookie> waitForConsentCookies(BrowserContext context, Page page) {
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

    public static Cookie findCookie(List<Cookie> cookies, String name) {
        return cookies.stream()
                .filter(cookie -> cookie.name.equals(name))
                .findFirst()
                .orElse(null);
    }

    public static boolean isAnalyticsCookie(String name) {
        return name.equals("_ga") || name.equals("_gid") || name.startsWith("_ga_")
                || name.startsWith("s_vi_") || name.equals("s_cc") || name.equals("s_fid")
                || name.equals("Gdyn") || name.startsWith("AMCV_") || name.startsWith("AMCVS_")
                || name.equals("TRADEDOUBLER") || name.equals("TradeDoublerGUID");
    }

    public static boolean isMarketingCookie(String name) {
        return name.equals("cookieSEG") || name.equals("cookieSEG__details") || name.equals("GAD");
    }

    private static boolean hasCookie(List<Cookie> cookies, String name) {
        return findCookie(cookies, name) != null;
    }
}
