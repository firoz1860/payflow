# Verified Razorpay Checkout Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox syntax for tracking.

**Goal:** Complete and prove a Razorpay test payment through Payflow's website, verified backend state, durable events, and balanced ledger.

**Architecture:** Keep provider network calls in provider-service, ownership and state transitions in payment-service, and checkout UI in frontend. Use separate provider order/payment identifiers and a single validated state-application path. Browser success never independently authorizes capture.

**Tech Stack:** Java 21, Spring Boot 3.3.5, PostgreSQL/Flyway, Kafka outbox, React 18, TypeScript, Vite, Testcontainers.

**Spec:** ../specs/2026-10-03-razorpay-checkout-design.md

## Global Constraints

- TEST routing allows sandbox or razorpay; sandbox remains the default.
- LIVE creation is rejected during this stage; no real-money charges or paid infrastructure upgrades.
- Secrets remain backend-only; record sanitized references and outcomes.
- Cross-tenant payment references return 404.
- Checkout polling is bounded to 60 seconds and stops on unmount or terminal status.
- Preserve the verified two-connection backend pool configuration.
- Missing real-provider credentials or unavailable methods are BLOCKED, never PASS.
- Merchant delivery webhooks, refunds, settlements, Stripe, and whole-platform production readiness require subsequent projects.

## Review Focus

1. Concurrent callback and webhook capture: exactly one capture event and ledger posting (Task 3).
2. Provider creates order but response is lost: no second order on retry (Task 2).
3. Browser dismissal after payment authorization: leave authoritative state intact (Task 4).
4. New backend with old frontend and old persisted rows: preserve sandbox and nullable-column compatibility (Tasks 1, 5).
5. Provider identity, amount or currency mismatches: reject evidence before capture (Task 3).

## File boundaries and shared contracts

Provider paths below are under `provider-service/src/main/java/com/payflow/provider/`; payment paths are under `payment-service/src/main/java/com/payflow/payment/`.

- Provider `gateway/razorpay/RazorpayCheckoutService.java`: provider order lookup, signed evidence verification, and authoritative payment fetch.
- Provider `gateway/razorpay/RazorpayOrderAttempt.java` and `repository/RazorpayOrderAttemptRepository.java`: durable order creation uncertainty and concurrency protection.
- Provider `gateway/razorpay/RazorpayCheckoutDtos.java`: `CheckoutOptions(provider, mode, keyId, orderId, amountMinor, currency)`, `VerificationRequest(orderId, paymentId, signature)`, `VerifiedPayment(orderId, paymentId, amountMinor, currency, status)`; strings except long amountMinor and GatewayStatus status.
- Payment `service/PaymentCheckoutService.java`: tenant binding and orchestration, without provider HTTP under a row lock.
- Payment `dto/CheckoutDtos.java`: matching external options/evidence DTOs; never contains a secret.
- Frontend `src/components/RazorpayCheckout.tsx`: shared checkout launcher and verification UX.
- Frontend `src/lib/razorpay.ts`: single script loader and typed provider interface.

### Task 1: Explicit TEST routing and compatible payment identity storage

**Files:** modify provider `config/ProviderProperties.java`, `gateway/PaymentGatewayRegistry.java`, `src/main/resources/application.yml`; payment `domain/PaymentAttempt.java`; create payment `src/main/resources/db/migration/V4__razorpay_payment_identity.sql`; create provider `src/test/java/com/payflow/provider/gateway/PaymentGatewayRegistryTest.java`; extend payment `domain/SchemaMappingTest.java`.

**Interfaces:** registry retains `PaymentGateway selectFor(String environment, String currency, String paymentMethod)`; configuration adds `testProvider` (sandbox default). PaymentAttempt adds nullable `providerEntityPaymentId` and `attachProviderEntityPaymentId(String id)` without overwriting legacy providerPaymentId.

- [ ] Write failing tests named `testRoutesToConfiguredRazorpay`, `missingTestSecretFailsStartup`, `liveCreationRejected`, and `legacyAttemptHasNullableEntityId`; assert sandbox default, configured Razorpay selection, mismatched key rejection, and unchanged old order ID.
- [ ] Run `mvn -B -ntp -pl provider-service,payment-service -am test`; confirm new assertions fail for current behavior.
- [ ] Implement configuration validation and routing, additive column/entity mapping, and unique non-null provider/entity identity constraint. Reject unsupported real-provider method/currency explicitly.
- [ ] Run the same command and `mvn -B -ntp -pl payment-service -am verify`; expect all tests and schema validation to pass.
- [ ] Commit the routing and identity change.

### Task 2: Durable provider order creation and verification contract

**Files:** create provider checkout service/DTOs/order attempt/repository listed above, `src/main/resources/db/migration/V3__razorpay_order_attempts.sql`, and `src/test/java/com/payflow/provider/gateway/razorpay/RazorpayCheckoutServiceTest.java`; modify provider `gateway/razorpay/RazorpayPaymentGateway.java` and `controller/InternalProviderController.java`.

**Interfaces:** `CheckoutOptions checkout(String storedOrderId)`, `VerifiedPayment verify(VerificationRequest request)`, and `VerifiedPayment fetch(String paymentId)` on RazorpayCheckoutService. Internal GET `/internal/providers/razorpay/orders/{orderId}/checkout` and POST `/internal/providers/razorpay/verify` return those contracts. Durable create-attempt states are CREATING, CREATED, RECONCILIATION_REQUIRED; unique Payflow payment reference.

- [ ] Verify current official Orders API idempotency/lookup capabilities; record source and chosen reconciliation behavior in code documentation. Do not assume the custom idempotency header is effective.
- [ ] Write failing tests `lostCreateResponseDoesNotCreateAgain`, `concurrentCreateMakesOneCall`, `validSignatureFetchesBoundPayment`, `invalidSignatureDoesNotFetch`, `wrongModeRejected`, `minorUnitsExact`, and `providerTimeoutIsRetryable`; mock actual HTTP request paths and bodies.
- [ ] Run `mvn -B -ntp -pl provider-service -am test`; expect new tests to fail.
- [ ] Implement durable claim before order creation; ambiguous network outcomes become RECONCILIATION_REQUIRED. Reuse known orders; block blind retry when no supported lookup establishes the original order. Compute HMAC over stored order ID and supplied payment ID, compare constantly, fetch authoritative entity. Return public Checkout options instead of a checkout.js navigation link. Validate amounts without rounding overflow and bound metadata to provider limits.
- [ ] Run unit tests and provider migration integration validation; expect no duplicate order call and no credential leakage. Add provider Failsafe/Testcontainers wiring if absent so the new migration test actually executes in CI.
- [ ] Commit provider creation and verification contracts.

### Task 3: Tenant-owned checkout, state validation, and webhook convergence

**Files:** create payment checkout service/DTOs listed above; modify payment `controller/PaymentController.java`, `client/ProviderClient.java`, `service/PaymentService.java`, `repository/PaymentRepository.java`, `repository/PaymentAttemptRepository.java`, and `messaging/ProviderEventConsumer.java`; provider `controller/ProviderWebhookController.java`, `service/ProviderWebhookService.java`, `gateway/PaymentGateway.java`, and Razorpay adapter; create payment `service/PaymentCheckoutServiceTest.java` and `integration/RazorpayCaptureIT.java`; extend existing provider webhook tests.

**Interfaces:** PaymentCheckoutService `CheckoutDtos.Options checkout(PayFlowPrincipal principal, String reference)` and `PaymentDtos.PaymentResponse verify(PayFlowPrincipal principal, String reference, CheckoutDtos.VerificationRequest evidence)`; ProviderClient internal methods consume Task 2 contracts. Normalized provider evidence additionally carries actual payment ID, order/QR association, amount and currency. Shared validated state application preserves existing sandbox compatibility.

- [ ] Write failing tests for cross-tenant 404, substituted order/payment ID, amount/currency mismatch, captured checkout rejection, repeated verify, callback racing webhook, delayed authorization after capture, unsupported events, and raw-body signature rejection. Assert one capture event, zero downgrade, zero extra ledger posting.
- [ ] Run `mvn -B -ntp -pl payment-service,provider-service,ledger-service -am test`; confirm new behavior fails.
- [ ] Implement additive external endpoints and permissions from the spec. Obtain provider evidence outside transactions, then lock and revalidate stored association inside a short transactional state-application method. Persist entity ID, apply only legal transitions, and emit capture once. Read provider event-ID header with stable fallback; carry amount/currency into consumer validation. Unsupported events produce no fabricated failure. Use unique ledger source constraints and existing deduplication.
- [ ] Run `mvn -B -ntp verify`; explicitly confirm RazorpayCaptureIT runs against real PostgreSQL/Kafka and checks a single balanced ledger result after concurrent delivery.
- [ ] Commit ownership, verification, and webhook convergence.

### Task 4: Browser Checkout and bounded status refresh

**Files:** create frontend Razorpay component/loader listed above, `src/components/RazorpayCheckout.test.tsx`, and `src/lib/razorpay.test.ts`; modify `src/services/paymentService.ts`, `src/types.ts`, `src/pages/CreatePaymentPage.tsx`, `src/pages/PaymentDetailPage.tsx`, `package.json`, `package-lock.json`, and `.github/workflows/frontend-ci.yml`.

**Interfaces:** `loadRazorpay(): Promise<RazorpayConstructor>`; `<RazorpayCheckout paymentReference={string} onUpdated={(payment: Payment) => void} />` uses the existing exported payment response type (reuse its actual name when defining prop). Payment service `getCheckout(reference)` and `verifyCheckout(reference, evidence)` return Task 3 contracts.

- [ ] Add Vitest/jsdom and React Testing Library as development dependencies; define `npm test` and CI test step. Write failing tests for one script load, script failure/retry, duplicate clicks, dismissal without cancellation, signed evidence submission, provider decline, pending verification, 60-second timeout, and polling cleanup on unmount.
- [ ] Run `npm test -- --run` in frontend; confirm assertions fail for missing Checkout behavior.
- [ ] Implement typed Checkout launch with server options and no raw card inputs. Submit handler evidence, display backend status, poll no longer than 60 seconds, and expose manual refresh afterward. Keep existing sandbox/QR paths functional and label provider/mode accurately.
- [ ] Run `npm test -- --run` and `npm run build`; expect tests, TypeScript, and Vite to pass.
- [ ] Commit the Checkout UI and frontend test wiring.

### Task 5: Release verification and real Razorpay acceptance

**Files:** create `docs/testing/razorpay-acceptance.md`; update `docs/API.md`, `docs/DEPLOYMENT_RENDER_VERCEL.md`; record sanitized execution evidence in `docs/testing/razorpay-results.md` during execution.

**Interfaces:** consume completed endpoints and UI from Tasks 1–4; evidence statuses PASS, FAIL, BLOCKED.

- [ ] Run complete Java/frontend CI, package builds, schema checks, and existing sandbox/auth regressions. Confirm new integration tests execute and do not merely compile.
- [ ] Open a PR and deploy the compatible backend/frontend changes to a controlled TEST environment. Keep production routing on sandbox until provider test configuration is validated. Do not change pools to one connection.
- [ ] Configure Razorpay test keys and webhook secret through secure deployment settings with the account owner when necessary; configure provider dashboard webhook URL. Record missing access as BLOCKED without asking for secrets in chat.
- [ ] Through the actual website complete provider-supported success, decline, dismissal/retry, duplicate verification, webhook redelivery, delayed webhook/reconciliation, and wrong-tenant cases. Correlate actual Razorpay order/payment references with Payflow state, published outbox records, and one balanced ledger posting. Mark unavailable methods BLOCKED.
- [ ] Review compatibility with old rows/clients and test-mode rollback. Merge only after automated gates pass; report real-provider failures/blockers clearly instead of claiming full production readiness. Keep live charges and paid hosting as separate reviewed decisions.
- [ ] Commit sanitized acceptance evidence and operational documentation.

## Self-review

Spec coverage: routing/credentials (Task 1), order/payment identity and uncertainty (Tasks 1–2), ownership/verification/convergence (Task 3), Checkout/refresh (Task 4), real-provider evidence and rollout (Task 5). Review Focus conditions map to explicit tests above. Scope excludes remaining independent subsystems. No step treats mocked Checkout, missing credentials, or health alone as production evidence.

## Execution handoff

Await user review and execution-method selection. Recommended: native execution, because provider, payment and frontend contracts are tightly coupled and one implementation context keeps changes consistent. Alternative: subagent-driven execution with independent task reviews. Implementation starts after the user approves this plan and selects an approach.
