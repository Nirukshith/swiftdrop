#!/usr/bin/env bash
# M2 end-to-end check: customer tracking, live SSE updates, history, PDF receipt and rating,
# against the running Docker core stack, through the gateway.
#
#   docker compose -f deploy/docker-compose.yml --env-file deploy/.env --profile core up -d
#   bash docs/m2-e2e/run.sh            # writes its files to ./m2-e2e-out
#
# Needs bash, curl, node and docker on the host. Nothing is inserted into parcel_db: the customer
# creates the parcel through the API. The only setup is demo pricing data for Colombo and Kandy in
# ops_db (skipped when present), because no admin user is seeded to create it through the API.
# Every check prints "ok" or "FAIL"; the script exits with the number of failures.
set -u
GW=${GW:-http://localhost:8080}
PARCEL=${PARCEL:-http://localhost:8082}   # internal status endpoint, as courier/dispatch services call it
HERE=$(cd "$(dirname "$0")" && pwd)
OUT=${OUT:-./m2-e2e-out}
mkdir -p "$OUT"
PG=$(docker ps --format '{{.Names}}' | grep -m1 postgres)
FRONTEND=$(docker ps --format '{{.Names}} {{.Image}}' | awk '/frontend/ {print $1; exit}')
RUN=$(date +%s)
PASS="E2e-$(node -e 'console.log(require("crypto").randomBytes(6).toString("hex"))')!"
COURIER=c0a1e400-0000-4000-8000-$(printf '%012d' "$RUN")
FAILURES=0

json() { node -e 'let s="";process.stdin.on("data",d=>s+=d).on("end",()=>{try{const j=JSON.parse(s);console.log(process.argv[1].split(".").reduce((o,k)=>o==null?o:o[k],j))}catch{console.log("")}})' "$1"; }
now() { node -e 'console.log(Date.now())'; }
say() { printf '\n### %s\n' "$*"; }
check() { # name expected actual
  if [ "$2" = "$3" ]; then echo "ok    $1 ($3)"; else echo "FAIL  $1: expected $2, got $3"; FAILURES=$((FAILURES + 1)); fi
}
code() { curl -s -o /dev/null -w '%{http_code}' "$@"; }

say "0. Setup: demo pricing data (postgres container: $PG)"
docker exec -i "$PG" psql -q -U swiftdrop -d ops_db <<'SQL'
INSERT INTO zones (name, served, base_fee_lkr) VALUES ('E2E Western', true, 300), ('E2E Central', true, 400) ON CONFLICT (name) DO NOTHING;
INSERT INTO zone_districts (zone_id, district) SELECT id, 'Colombo' FROM zones WHERE name = 'E2E Western' ON CONFLICT DO NOTHING;
INSERT INTO zone_districts (zone_id, district) SELECT id, 'Kandy' FROM zones WHERE name = 'E2E Central' ON CONFLICT DO NOTHING;
INSERT INTO pricing_rules (kind, rule_key, minimum_weight_kg, maximum_weight_kg, charge_lkr) VALUES ('WEIGHT_BAND', 'E2E_0_10', 0, 10, 200) ON CONFLICT DO NOTHING;
INSERT INTO pricing_rules (kind, rule_key, charge_lkr) VALUES ('SIZE', 'MEDIUM', 100) ON CONFLICT DO NOTHING;
SQL

say "1. Register and log in two customers"
token() {
  curl -s -o /dev/null -X POST "$GW/api/v1/auth/register" -H 'Content-Type: application/json' \
    -d "{\"email\":\"$1\",\"password\":\"$PASS\",\"fullName\":\"$2\",\"phoneNumber\":\"+94771234567\"}"
  curl -s -X POST "$GW/api/v1/auth/login" -H 'Content-Type: application/json' \
    -d "{\"email\":\"$1\",\"password\":\"$PASS\"}" | json accessToken
}
A=$(token "m2-e2e-a-$RUN@example.com" "Customer A"); B=$(token "m2-e2e-b-$RUN@example.com" "Customer B")
check "customer A logged in" yes "$([ -n "$A" ] && echo yes || echo no)"
check "customer B logged in" yes "$([ -n "$B" ] && echo yes || echo no)"

say "2. Customer A creates a parcel with a Tamil recipient and a Sinhala city"
# The body is written as a UTF-8 file by node: on Windows, non-Latin text in a command-line
# argument can reach curl as "?" characters.
node -e 'require("fs").writeFileSync(process.argv[1], JSON.stringify({
  pickupAddress: {line1: "45 Galle Road", city: "Colombo 03", district: "Colombo", postalCode: "00300"},
  dropoffAddress: {line1: "12 Temple Road", city: "මහනුවර", district: "Kandy", postalCode: "20000"},
  weightKg: 2.5, size: "MEDIUM", recipientName: "சுனில் குமார்",
  recipientPhone: "+94771234567", notes: "Gate code 4321"}), "utf8")' "$OUT/create-parcel.json"
CREATED=$(curl -s -w '\n%{http_code}' -X POST "$GW/api/v1/parcels" -H "Authorization: Bearer $A" \
  -H 'Content-Type: application/json; charset=utf-8' --data-binary @"$OUT/create-parcel.json")
BODY=$(echo "$CREATED" | sed '$d')
check "POST /api/v1/parcels" 201 "$(echo "$CREATED" | tail -1)"
ID=$(echo "$BODY" | json id); TN=$(echo "$BODY" | json trackingNumber)
echo "parcel id=$ID trackingNumber=$TN fee=LKR $(echo "$BODY" | json fee)"
[ -n "$ID" ] || { echo "cannot continue without a parcel"; exit 99; }

say "3. Public lookup without login is masked"
curl -s "$GW/api/v1/tracking/$TN" > "$OUT/public.json"; cat "$OUT/public.json"; echo
check "public status" CREATED "$(json status < "$OUT/public.json")"
check "recipient masked (Tamil initials)" "U+0B9A*** U+0B95***" "$(node -e 'const j=JSON.parse(require("fs").readFileSync(process.argv[1],"utf8"));console.log([...j.recipientNameMasked].map(c=>c.codePointAt(0)<128?c:"U+"+c.codePointAt(0).toString(16).toUpperCase().padStart(4,"0")).join(""))' "$OUT/public.json")"
check "no address, phone, notes or fee" none "$(grep -q -E 'Temple|\+9477|Gate code|"fee"' "$OUT/public.json" && echo found || echo none)"

say "4. Live stream through the gateway, timed (NF-01: under 2 s)"
node "$HERE/sse-timed.js" "$GW/api/v1/tracking/$TN/stream" > "$OUT/sse.txt" & SSE=$!
sleep 2
: > "$OUT/changes.txt"
change() { # from to
  local extra=""
  [ "$2" = DELIVERED ] && extra=', "proof": {"recipientName": "Recipient", "proofPhotoPath": "proofs/e2e.jpg"}'
  local t; t=$(now)
  local seq; seq=$(curl -s -X PATCH "$PARCEL/internal/v1/parcels/$ID/status" -H 'Content-Type: application/json' \
    -d "{\"status\":\"$2\",\"expectedCurrentStatus\":\"$1\",\"courierId\":\"$COURIER\"$extra}" | json sequence)
  echo "$t $seq $2" >> "$OUT/changes.txt"
}
check "assign courier" 200 "$(code -X PUT "$PARCEL/internal/v1/parcels/$ID/assignment" -H 'Content-Type: application/json' -d "{\"courierId\":\"$COURIER\",\"assignedBy\":\"dispatcher-e2e\"}")"
change CREATED PICKED_UP; sleep 1
change PICKED_UP IN_TRANSIT; sleep 1
change IN_TRANSIT OUT_FOR_DELIVERY; sleep 1
change OUT_FOR_DELIVERY DELIVERED; sleep 3
kill $SSE 2>/dev/null; wait $SSE 2>/dev/null
cut -d' ' -f2- "$OUT/sse.txt"
node -e '
const fs=require("fs");const [changes,sse]=process.argv.slice(1,3).map(f=>fs.readFileSync(f,"utf8").trim().split("\n"));
const arrived={};let id=null;for(const l of sse){const [t,...r]=l.split(" ");const line=r.join(" ");if(line.startsWith("id:"))id=line.slice(3).trim();if(line.startsWith("data:")&&id)arrived[id]=Number(t);}
let worst=0,missing=0;for(const c of changes){const [sent,seq,status]=c.split(" ");const ms=arrived[seq]-Number(sent);if(isNaN(ms))missing++;else worst=Math.max(worst,ms);console.log(`${status.padEnd(17)} sequence ${seq}: ${isNaN(ms)?"NOT RECEIVED":ms+" ms"}`);}
fs.writeFileSync(process.argv[3],`${missing} ${worst}`);' "$OUT/changes.txt" "$OUT/sse.txt" "$OUT/latency.txt"
read -r MISSING WORST < "$OUT/latency.txt"
check "every status change reached the stream" 0 "$MISSING"
check "slowest update under 2000 ms" yes "$([ "$WORST" -lt 2000 ] && echo yes || echo "no ($WORST ms)")"
check "event data has exactly the 4 public fields" 4 "$(grep -m1 'data:{' "$OUT/sse.txt" | sed 's/^[0-9]* data://' | node -e 'let s="";process.stdin.on("data",d=>s+=d).on("end",()=>console.log(Object.keys(JSON.parse(s)).length))')"

say "5. Reconnect with Last-Event-ID 3 replays only the missed changes"
timeout 5 node "$HERE/sse-timed.js" "$GW/api/v1/tracking/$TN/stream" 3 > "$OUT/replay.txt"
cut -d' ' -f2- "$OUT/replay.txt"
check "replayed ids" "4 5 6" "$(grep -o 'id:[0-9]*' "$OUT/replay.txt" | cut -d: -f2 | tr '\n' ' ' | sed 's/ $//')"

say "6. History is owner-only"
check "A's list contains the parcel" true "$(curl -s -H "Authorization: Bearer $A" "$GW/api/v1/parcels?size=20" | node -e 'let s="";process.stdin.on("data",d=>s+=d).on("end",()=>console.log(JSON.parse(s).content.some(p=>p.trackingNumber===process.argv[1])))' "$TN")"
check "B's list does not" false "$(curl -s -H "Authorization: Bearer $B" "$GW/api/v1/parcels?size=20" | node -e 'let s="";process.stdin.on("data",d=>s+=d).on("end",()=>console.log(JSON.parse(s).content.some(p=>p.trackingNumber===process.argv[1])))' "$TN")"
check "A /history" 200 "$(code -H "Authorization: Bearer $A" "$GW/api/v1/parcels/$ID/history")"
check "B /history" 403 "$(code -H "Authorization: Bearer $B" "$GW/api/v1/parcels/$ID/history")"
check "no token /history" 401 "$(code "$GW/api/v1/parcels/$ID/history")"
check "timeline" "CREATED PICKED_UP IN_TRANSIT OUT_FOR_DELIVERY DELIVERED" "$(curl -s -H "Authorization: Bearer $A" "$GW/api/v1/parcels/$ID/history" | node -e 'let s="";process.stdin.on("data",d=>s+=d).on("end",()=>console.log(JSON.parse(s).entries.map(e=>e.toStatus).join(" ")))')"

say "7. PDF receipt (Tamil recipient, Sinhala city)"
check "A receipt" 200 "$(curl -s -o "$OUT/receipt.pdf" -w '%{http_code}' -H "Authorization: Bearer $A" "$GW/api/v1/parcels/$ID/receipt")"
check "receipt is a PDF" "%PDF-" "$(head -c 5 "$OUT/receipt.pdf")"
check "Tamil font embedded" yes "$(grep -a -q 'NotoSansTamil' "$OUT/receipt.pdf" && echo yes || echo no)"
check "Sinhala font embedded" yes "$(grep -a -q 'NotoSansSinhala' "$OUT/receipt.pdf" && echo yes || echo no)"
check "B receipt" 403 "$(code -H "Authorization: Bearer $B" "$GW/api/v1/parcels/$ID/receipt")"
check "no token receipt" 401 "$(code "$GW/api/v1/parcels/$ID/receipt")"

say "8. Delivery rating"
rate() { code -X POST "$GW/api/v1/parcels/$ID/rating" -H "Authorization: Bearer $1" -H 'Content-Type: application/json' -d "$2"; }
check "B rates" 403 "$(rate "$B" '{"score": 1}')"
check "A rates 7" 400 "$(rate "$A" '{"score": 7}')"
check "A rates 5" 201 "$(rate "$A" '{"score": 5, "comment": "On time"}')"
check "A rates again" 409 "$(rate "$A" '{"score": 1}')"
check "A reads rating" 5 "$(curl -s -H "Authorization: Bearer $A" "$GW/api/v1/parcels/$ID/rating" | json score)"

say "9. Every change stored for replay by the Kafka consumer"
check "tracking_event rows" "3 4 5 6" "$(docker exec "$PG" psql -tA -U swiftdrop -d parcel_db -c "SELECT string_agg(sequence::text, ' ' ORDER BY sequence) FROM tracking_event WHERE tracking_number = '$TN'")"

say "10. Public rate limit per visitor behind the gateway (burst 10)"
# Visitor 1 is this machine; visitor 2 calls the gateway from inside the frontend container, so the
# two have different IP addresses. Each visitor should have their own limit. This check fails until
# the gateway forwards the client address (see docs/m2-week7-tracking-hardening-verification.md).
# Wait a minute between runs so the limit refills.
LOOKUP="/api/v1/tracking/SD-20991231-RATE00"
V1=""; for i in $(seq 1 12); do V1="$V1 $(code "$GW$LOOKUP")"; done
echo "visitor 1, 12 lookups:$V1"
check "visitor 1 is limited after the burst" 429 "$(echo "$V1" | awk '{print $NF}')"
if [ -n "$FRONTEND" ]; then
  V2=$(docker exec "$FRONTEND" sh -c "wget -q -S -O /dev/null http://gateway$LOOKUP 2>&1" | awk '/HTTP\// {print $2; exit}')
  echo "visitor 2 (container $FRONTEND), first lookup: $V2"
  check "visitor 2 has their own limit" 404 "$V2"
else
  echo "no frontend container running; visitor 2 check skipped"
fi

say "Result: $FAILURES failed check(s)"
echo "trackingNumber=$TN id=$ID" > "$OUT/parcel.txt"
exit $FAILURES
