# Payment Detail Recovery Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Keep payment detail failures recoverable without allowing stale or duplicate actions.

**Architecture:** Use the existing LoadErrorState and a request sequence in PaymentDetailPage. Invalidate previous requests on navigation and newer reads; guard cancellation by the displayed identity and a synchronous in-flight flag.

**Tech Stack:** Existing React 18, TypeScript, Vitest and React Testing Library.

**Spec:** `docs/superpowers/specs/2026-10-13-payment-detail-recovery.md`

## Global Constraints

- Preserve the 60-second polling window and five-second interval.
- Cancellation requires `payments:create` and the displayed payment identity.
- Retry reads status; it never recreates a payment or replays cancellation.
- No dependencies, migrations, credentials or backend contracts change.

## Review Focus

- A late success or 404 for an old route must not replace the current record.
- A poll started before manual refresh must not regress a confirmed capture.
- A late cancellation must not modify another payment after navigation.
- Double submission must produce one cancellation request.
- An uncertain cancellation requires read-only recovery before actions return.

---

### Task 1: Recover payment details and guard actions

**Files:**
- Modify: `frontend/src/pages/PaymentDetailPage.tsx`
- Create: `frontend/src/pages/PaymentDetailRecovery.test.tsx`

**Interfaces:**
- Consumes: `getPayment(reference: string): Promise<Payment>`, `cancelPayment(reference: string, reason?: string): Promise<Payment>`.
- Produces: the existing `PaymentDetailPage()` component and routes, with persistent read errors and guarded responses.

- [x] Write regressions for outage retry, confirmed 404, late route success/failure, stale poll after refresh, late cancellation, double submission, missing permission, incorrect response identity and uncertain cancellation recovery.
- [x] Run `npm test -- src/pages/PaymentDetailRecovery.test.tsx`; verify the failures reproduce visible incorrect states or unsafe actions on the existing implementation.
- [x] Add the existing LoadErrorState, request/lifecycle guards and a cancellation in-flight flag to PaymentDetailPage. Hide stale identity and unresolved status; gate actions by permission and current payment.
- [x] Run the focused tests, then `npm test` and `npm run build`; all must pass. Run `git diff --check`.
- [ ] Commit, request independent review, publish the reviewed branch and require current-head CI plus both Vercel previews before merging. Verify production frontend after deployment.
