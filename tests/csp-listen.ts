import { Page } from '@playwright/test';

// R-3: every page under test reports content security policy violations as console errors, which the tests' error
// watch fails on (report-only and enforced alike). Protocol messages keep their order, so this is in place before the
// page's first navigation even when not awaited.
export function listenCsp(page: Page) {
  void page.addInitScript(() => document.addEventListener('securitypolicyviolation', e =>
    console.error(`CSP violation: ${e.violatedDirective} ${e.blockedURI} ${e.sourceFile}:${e.lineNumber}`)));
}
