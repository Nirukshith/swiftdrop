import { environment } from '../environments/environment';

/** Starts the MSW service worker before the app boots, so the first requests are already mocked. */
export async function startMocks(): Promise<void> {
  if (!environment.useMocks) {
    return;
  }
  const { worker } = await import('./browser');
  await worker.start({
    serviceWorker: { url: '/mockServiceWorker.js' },
    // Requests without a handler (e.g. auth, other services) go to the network / dev proxy.
    onUnhandledRequest: 'bypass',
  });
  console.info('[SwiftDrop] Mock API is ON (MSW). Handled: parcels, addresses, tracking.');
}
