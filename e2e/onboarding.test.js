/**
 * Phase 1 Integration Tests — SafeRing Onboarding
 *
 * Covers the full 3-step onboarding wizard verifying:
 * 1. Welcome screen ("Meet SafeRing" + "Get Started")
 * 2. Call Protection screen ("Call Protection" + "Enable Call Screening")
 * 3. All Set screen ("You're All Set!" + "Start Protection")
 * 4. Home screen loads after completion
 */
describe('Onboarding Flow', () => {
  beforeAll(async () => {
    await device.launchApp({ delete: true });
  });

  it('should show the Welcome step', async () => {
    await expect(element(by.text('Meet SafeRing'))).toBeVisible();
    await expect(element(by.text('AI-powered protection against phone scams'))).toBeVisible();
    await expect(element(by.text('Get Started'))).toBeVisible();
  });

  it('should navigate to Call Protection step', async () => {
    await element(by.text('Get Started')).tap();
    await waitFor(element(by.text('Call Protection')))
      .toBeVisible()
      .withTimeout(3000);
    await expect(element(by.text('Stop scams before they ring'))).toBeVisible();
    await expect(element(by.text('Enable Call Screening'))).toBeVisible();
  });

  it('should navigate to All Set step', async () => {
    await element(by.text('Enable Call Screening')).tap();
    await waitFor(element(by.text("You're All Set!")))
      .toBeVisible()
      .withTimeout(3000);
    await expect(element(by.text('Nothing else to configure'))).toBeVisible();
  });

  it('should complete onboarding and show Home screen', async () => {
    await element(by.text('Start Protection')).tap();
    await waitFor(element(by.text('SafeRing')))
      .toBeVisible()
      .withTimeout(5000);
  });
});
