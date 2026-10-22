# Report Center And Price Advice Integration

## Scope

Vue 3 frontend, Spring Boot backend and the existing Python historical price API.
This work does not replace group authentication, change legacy accounts, or require
overwriting a shared database. Database compatibility work is outside this module.

## Request Flow

1. Vue uses the group's existing Axios bearer-token interceptor and /api proxy.
2. POST /api/ai/houses/{houseId}/price-predictions checks ai:use and house scope.
3. Spring Boot reads the house features, excludes listing price and customer budget,
   and POSTs JSON to ${AI_PRICE_BASE_URL}/ai/predict/price (default port 8001).
4. Connect timeout is 2 seconds, price response timeout 10 seconds, response limit
   1 MiB. The existing Python API envelope is code/data, with snake_case fields.
5. PricePredictionContract validates historical usage, numeric prices, interval,
   coverage, metadata and quality lists before saving a prediction and request log.
6. Vue reads the saved camelCase record; nested interval keys stay snake_case.
   DEMO_ONLY is a historical reference, UNAVAILABLE has null prices, FAILED has a
   request log but no successful prediction. Listing/deal prices are never changed.
7. GET /price-predictions reads history without invoking Python. Feature hashes
   flag old snapshots as stale. GET /requests?kind=PRICE lists price calls only.

Configure AI_PRICE_BASE_URL on the Spring Boot host, not in browser code. Start the
existing service with python ai_service/price_predict/start_service.py. Its ready
endpoint is /ready. Do not expose Python directly to the browser or embed secrets.

## Review Routes

- /dashboard: authorized live report aggregates, four views and 12 chart types.
- /reports/preview: isolated deterministic examples without business API calls.
- /house -> price advice -> /price-advice/:houseId: house-based prediction page.

The report center includes date/store filters, same-dimension focus, metric/sort
selection, detail rows, CSV/PNG exports, chart enlargement and saved local views.
The pricing page includes listing/reference price differences, a dual-marker
interval chart, explanations, quality flags, version/input
snapshots, stale notices, result history and request history. History/log errors
are isolated so a failed history fetch does not hide the house or current result.

## Files For Later Group Review

Frontend: package.json/package-lock.json, src/views/Dashboard.vue,
src/views/PriceAdvice.vue, src/router/index.js and src/reports/.
Backend: ai/AiService.java, ai/AiController.java, ai/PricePredictionContract.java,
the new ai/PricePredictionContractTest.java and updated BackendIntegrationTest.java.
The group already has the report controllers and Python model service.

The worktree also contains earlier LOCAL database compatibility edits to V001,
V003, a local V004, database/GROUP_INTEGRATION.md and a localhost proxy adjustment.
Those are not prerequisites to blindly apply to the group database. Review them
separately with the database owner; do not upload all dirty files indiscriminately.
Never upload ../.local-integration, credentials, SQL backups, node_modules or logs.

## Verification

Frontend: npm run test:reports; npm run test:pricing; npm run build.
Backend: Java 17, mvn test; mvn package. Python: unittest discover under
ai_service/price_predict/tests. Backend tests use independent H2 and mocked HTTP.
Live checks must separately verify the deployed group's schema and account scope.

Database compatibility is not solved by passing H2 tests. Known upstream dump
differences (bootstrap table, legacy account fields and completed_time nullability)
remain outside this feature scope. Local review uses the existing isolated test DB.
