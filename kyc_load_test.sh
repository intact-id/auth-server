#!/bin/bash

# ============================================
# KYC Load Test Script - 100 Concurrent Requests
# With Alternating Images
#
# Uses the client-credentials token flow: exchanges API key + secret for a
# short-lived access token ONCE, then reuses that token as a Bearer header on
# every /verify call — this is the actual scenario the token flow exists for
# (avoids the per-request DB lookup + secret-hash comparison the old
# X-API-Key/X-API-Secret path does on every single request).
# ============================================

BASE_URL="https://auth.169.58.131.169.sslip.io"
TOKEN_URL="$BASE_URL/api/v1/auth/token"
VERIFY_URL="$BASE_URL/api/v1/kyc/dev/verify/tier1"
API_KEY="ak_dev_p2duxyx6v89uklyjiomlguesxpegt1qk"
API_SECRET="as_dev_cixngtmociof8od4qud90vaptu_elyqr"

# Image sets - alternates between these
SELFIE_1="/home/frank/Pictures/Camera/two.png"
SELFIE_2="/home/frank/Pictures/Camera/1.png"

ID_FRONT_1="/home/frank/Pictures/Camera/test.png"
ID_FRONT_2="/home/frank/Pictures/Camera/one.png"

# Number of concurrent requests
TOTAL_REQUESTS=100

# Per-request curl timeouts, in seconds — without these a stalled server/proxy
# can hang a subshell (and therefore the final `wait`) indefinitely.
CONNECT_TIMEOUT=15
MAX_TIME=90

# Output directory for logs
LOG_DIR="./kyc_test_logs"
mkdir -p "$LOG_DIR"

# Colors for output
GREEN='\033[0;32m'
RED='\033[0;31m'
YELLOW='\033[1;33m'
NC='\033[0m'

echo -e "${YELLOW}========================================${NC}"
echo -e "${YELLOW}  KYC Load Test - $TOTAL_REQUESTS Concurrent Requests${NC}"
echo -e "${YELLOW}  (Alternating Images, token auth)${NC}"
echo -e "${YELLOW}========================================${NC}"
echo ""

# Check prerequisites
if ! command -v jq >/dev/null 2>&1; then
    echo -e "${RED}Error: jq is required to parse the token response (apt install jq / brew install jq)${NC}"
    exit 1
fi

for file in "$SELFIE_1" "$SELFIE_2" "$ID_FRONT_1" "$ID_FRONT_2"; do
    if [ ! -f "$file" ]; then
        echo -e "${RED}Error: File not found: $file${NC}"
        exit 1
    fi
done

echo -e "${GREEN}All image files found ✓${NC}"

# ── Exchange API key + secret for a short-lived access token, once ─────────
echo -e "${YELLOW}Requesting access token...${NC}"

token_response=$(curl --silent --location --request POST "$TOKEN_URL" \
    --header "X-API-Key: $API_KEY" \
    --header "X-API-Secret: $API_SECRET")

ACCESS_TOKEN=$(echo "$token_response" | jq -r '.data.accessToken // empty')
EXPIRES_IN=$(echo "$token_response" | jq -r '.data.expiresIn // empty')

if [ -z "$ACCESS_TOKEN" ]; then
    echo -e "${RED}Error: Failed to obtain access token. Response:${NC}"
    echo "$token_response"
    exit 1
fi

echo -e "${GREEN}Access token acquired ✓ (expires in ${EXPIRES_IN}s)${NC}"
echo -e "${GREEN}Starting load test at $(date)${NC}"
echo ""

# Initialize results file
echo "request_id,http_code,time_seconds" > "$LOG_DIR/results.csv"

START_TIME=$(date +%s.%N)

echo -e "${YELLOW}Launching $TOTAL_REQUESTS concurrent requests...${NC}"
echo ""

# Run all requests concurrently using subshells
for i in $(seq 1 $TOTAL_REQUESTS); do
    (
        request_id=$i
        first_name="TestUser${request_id}"
        last_name="Load${request_id}"
        id_number="12121212${request_id}"

        if [ $((request_id % 2)) -eq 0 ]; then
            selfie="$SELFIE_1"
            id_front="$ID_FRONT_1"
        else
            selfie="$SELFIE_2"
            id_front="$ID_FRONT_2"
        fi

        # Write out metrics on their own last line so parsing is unambiguous
        metrics=$(curl --silent --location "$VERIFY_URL" \
            --connect-timeout "$CONNECT_TIMEOUT" \
            --max-time "$MAX_TIME" \
            --header "Authorization: Bearer $ACCESS_TOKEN" \
            --form "firstName=$first_name" \
            --form "lastName=$last_name" \
            --form "dateOfBirth=2000-01-15" \
            --form "idNumber=$id_number" \
            --form "countryOfResidence=Kenya" \
            --form "selfie=@${selfie}" \
            --form "idFront=@${id_front}" \
            --write-out "%{http_code}|%{time_total}" \
            --output "$LOG_DIR/response_${request_id}.json")

        http_code=$(echo "$metrics" | cut -d'|' -f1)
        time_total=$(echo "$metrics" | cut -d'|' -f2)

        # Guard against empty/non-numeric codes (connection failures, timeouts)
        [ -z "$http_code" ] && http_code="000"
        [ -z "$time_total" ] && time_total="0"

        if [ "$http_code" = "200" ] || [ "$http_code" = "201" ]; then
            echo -e "${GREEN}[✓] Request $request_id: HTTP $http_code (${time_total}s)${NC}"
        else
            echo -e "${RED}[✗] Request $request_id: HTTP $http_code (${time_total}s)${NC}"
        fi

        echo "$request_id,$http_code,$time_total" >> "$LOG_DIR/results.csv"
    ) &
done

wait

END_TIME=$(date +%s.%N)
DURATION=$(echo "$END_TIME - $START_TIME" | bc)

echo ""
echo -e "${YELLOW}========================================${NC}"
echo -e "${YELLOW}  Test Complete${NC}"
echo -e "${YELLOW}========================================${NC}"
echo ""

# Statistics
TOTAL=$(wc -l < "$LOG_DIR/results.csv")
TOTAL=$((TOTAL - 1))

SUCCESS=$(awk -F',' 'NR>1 && ($2==200 || $2==201) {count++} END {print count+0}' "$LOG_DIR/results.csv")
FAILED=$((TOTAL - SUCCESS))

AVG_TIME=$(awk -F',' 'NR>1 {sum+=$3; count++} END {if(count>0) printf "%.3f", sum/count; else print "0"}' "$LOG_DIR/results.csv")

echo -e "Total Requests:    ${GREEN}$TOTAL${NC}"
echo -e "Successful:        ${GREEN}$SUCCESS${NC}"
echo -e "Failed:            ${RED}$FAILED${NC}"
echo -e "Total Duration:    ${YELLOW}${DURATION}s${NC}"
echo -e "Avg Response Time: ${YELLOW}${AVG_TIME}s${NC}"
echo -e "Requests/Second:   ${YELLOW}$(echo "scale=2; $TOTAL / $DURATION" | bc)${NC}"
echo ""
echo -e "Logs saved to: ${GREEN}$LOG_DIR/${NC}"
