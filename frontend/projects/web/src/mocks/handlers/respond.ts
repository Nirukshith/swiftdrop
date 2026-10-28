import { HttpResponse } from 'msw';
import { MockResult } from '../mock-db';

/** Converts a MockResult into an MSW response (JSON body, or empty for 204). */
export function respond(result: MockResult): HttpResponse<never> | Response {
  if (result.body === undefined) {
    return new HttpResponse(null, { status: result.status, headers: result.headers });
  }
  return HttpResponse.json(result.body as never, { status: result.status, headers: result.headers });
}

export async function readJson(request: Request): Promise<Record<string, unknown>> {
  try {
    const body = await request.json();
    return body && typeof body === 'object' ? (body as Record<string, unknown>) : {};
  } catch {
    return {};
  }
}
