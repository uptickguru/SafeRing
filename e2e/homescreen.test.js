/**
 * Phase 1 Integration Tests — SafeRing Home Screen
 *
 * Verifies the main dashboard after onboarding:
 * - Navigation title
 * - Protection status indicator
 * - Stats grid (Calls Blocked, SMS Filtered, Scam # Known)
 * - Tab bar navigation (Home, History, Report, Settings)
 */
describe('Home Screen', () => {
  beforeAll(async () => {
    // Launch with onboarding completed
    await device.launchApp({
      launchArgs: { hasCompletedOnboarding: 'YES' }
    });
  });

  it('should display the navigation title', async () => {
    await expect(element(by.text('SafeRing'))).toBeVisible();
  });

  it('should show protection status', async () => {
    // Status can be "Active" or "Needs Setup" depending on permissions
    await waitFor(element(by.text('Active'))).toBeVisible().withTimeout(5000);
  });

  it('should show stats grid', async () => {
    await expect(element(by.text('Calls\nBlocked'))).toBeVisible();
    await expect(element(by.text('SMS\nFiltered'))).toBeVisible();
    await expect(element(by.text('Scam #\nKnown'))).toBeVisible();
  });

  it('should navigate to History tab', async () => {
    await element(by.text('History')).tap();
    await expect(element(by.text('Call History'))).toBeVisible();
  });

  it('should navigate to Report tab', async () => {
    await element(by.text('Report')).tap();
    await expect(element(by.text('Report a Scam'))).toBeVisible();
  });

  it('should navigate to Settings tab', async () => {
    await element(by.text('Settings')).tap();
    await expect(element(by.text('Settings'))).toBeVisible();
  });

  it('should navigate back to Home tab', async () => {
    await element(by.text('Home')).tap();
    await expect(element(by.text('SafeRing'))).toBeVisible();
  });
});
