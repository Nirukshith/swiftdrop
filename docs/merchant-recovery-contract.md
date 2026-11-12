# Merchant recovery contract — #207

The user confirmed M10/M5 owner agreement and approved the cross-member scope
and explicit same-client replacement policy in this session. The additive API
is [merchant-recovery.yaml](../contracts/openapi/merchant-recovery.yaml).

M6 persists its operation UUID before calling auth. Auth commits user activation
and client provisioning atomically and retains only a fingerprint/identifiers and
secret hashes. Matching retries reconcile identifiers. A replay cannot return a
previous raw secret, and never creates another account/client. A changed profile,
foreign email/merchant association or multiple legacy clients conflicts.

When a secret response is lost, the ADMIN explicitly confirms replacing that
same client's secret. M6 persists/forwards the replacement UUID across retries;
auth records it transactionally with the new hash. Its replay does not rotate
again or reveal a stored secret. Another lost handoff requires a new explicit
confirmation. Already issued tokens retain their normal expiry. Do not claim
immediate revocation of previously issued stateless JWTs.

M6 serializes approval/rejection/recovery per merchant and records non-secret
ADMIN operation outcomes. Pending/rejected merchants cannot order. The existing
one-time approval response remains compatible; lists/read APIs expose no secrets.
The admin UI offers recovery when provisioning began or approval completed,
clears raw secrets on acknowledgement/navigation and blocks overlapping handoffs.

The implementation is separately tracked under #207 and consumes this contract;
#223 consumes recovery and verifies the complete merchant workflow. #208 supplies
the auth JWT audience required by the already merged pricing validator. This
contract PR does not merge any implementation or close the dependent issues.
