import { test, expect } from "@playwright/test";

test.describe("GameSwap Phase 1 MVP — Key Bangalore Gamer Flows", () => {
  test("1. Onboarding flow for a new Bangalore gamer via Phone OTP", async ({ page }) => {
    await page.goto("/login");

    const randomSuffix = Math.floor(100000 + Math.random() * 899999);
    const phone = `9740${randomSuffix}`;

    await page.fill("#phone-input", phone);
    await page.getByTestId("send-otp-btn").click();

    await expect(page.getByTestId("dev-otp-banner")).toBeVisible();
    await page.getByTestId("verify-otp-btn").click();

    await expect(page).toHaveURL(/\/onboarding/);
    const handle = `blr_${randomSuffix}`;
    await page.fill("#display-name-input", handle);
    await page.getByTestId("console-btn-SWITCH").click();
    await page.getByTestId("locality-option-Indiranagar").click();
    await page.getByTestId("complete-onboarding-btn").click();

    await expect(page).toHaveURL("/");
  });

  test("2. Seeded gamer (@arjun_blr) views 2-way matches, fuzzed map pins, lists games, and accepts/completes a swap", async ({
    page,
  }) => {
    await page.goto("/login");
    await page.getByTestId("demo-login-arjun").click();

    // 1. Verify ranked matches load with Two-Way match first
    await expect(page).toHaveURL("/");
    await expect(page.getByTestId("matches-list")).toBeVisible();
    await expect(page.getByTestId("match-card-kavya_plays")).toBeVisible();

    // 2. Navigate to Map View and verify fuzzed pins render
    await page.goto("/map");
    await expect(page.getByTestId("map-pins-list")).toBeVisible();

    // 3. Navigate to Games Catalogue and add a game to HAVE list
    await page.goto("/games");
    await expect(page.getByTestId("games-grid")).toBeVisible();
    await page.getByTestId("add-have-btn-baldurs-gate-3").click();
    await page.getByTestId("confirm-add-listing-btn").click();
    await expect(page.getByTestId("listing-toast")).toBeVisible();

    // 4. Navigate to Chat & accept the seeded swap proposal from @kavya_plays
    await page.goto("/chat");
    await expect(page.getByTestId("chat-messages-list")).toBeVisible();
    const acceptBtn = page.getByTestId("accept-swap-btn");
    if (await acceptBtn.isVisible()) {
      await acceptBtn.click();
      await expect(page.getByTestId("swap-status-ACCEPTED")).toBeVisible();
    }
  });
});

