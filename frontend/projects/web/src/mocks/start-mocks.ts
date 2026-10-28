/**
 * Default build: no mock code is bundled. The "mock" configuration in angular.json replaces this
 * file with start-mocks.mock.ts, which starts MSW when environment.useMocks is true.
 */
export async function startMocks(): Promise<void> {}
