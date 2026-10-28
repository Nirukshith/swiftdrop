import { http } from 'msw';
import { MockParcelDb } from '../mock-db';
import { readJson, respond } from './respond';

/** Customer parcel and address endpoints from contracts/openapi/parcel.yaml (M1). */
export function parcelHandlers(db: MockParcelDb, baseUrl = '') {
  const api = `${baseUrl}/api/v1`;
  return [
    http.get(`${api}/parcels`, ({ request }) => respond(db.listParcels(new URL(request.url).searchParams))),
    http.post(`${api}/parcels`, async ({ request }) => respond(db.createParcel(await readJson(request)))),
    http.get(`${api}/parcels/:id`, ({ params }) => respond(db.getParcel(String(params['id'])))),
    http.post(`${api}/parcels/:id/cancel`, async ({ params, request }) => {
      const body = await readJson(request);
      return respond(db.cancelParcel(String(params['id']), body['reason'] as string | undefined));
    }),

    http.get(`${api}/addresses`, () => respond(db.listAddresses())),
    http.post(`${api}/addresses`, async ({ request }) => respond(db.saveAddress(await readJson(request)))),
    http.get(`${api}/addresses/:id`, ({ params }) => respond(db.getAddress(String(params['id'])))),
    http.put(`${api}/addresses/:id`, async ({ params, request }) => respond(db.saveAddress(await readJson(request), String(params['id'])))),
    http.delete(`${api}/addresses/:id`, ({ params }) => respond(db.deleteAddress(String(params['id'])))),
  ];
}
