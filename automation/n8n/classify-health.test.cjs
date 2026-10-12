const { test } = require('node:test');
const assert = require('node:assert/strict');
const { classifyHealth, notificationForHealth } = require('./classify-health.cjs');
const timestamp = '2026-10-11T11:30:00.000Z';

test('only successful HTTP with the actual UP body is healthy', () => {
  assert.equal(classifyHealth({ statusCode: 200, body: { status: 'UP' } }, timestamp).healthy, true);
  for (const response of [
    { statusCode: 200, body: '<html>Sign in</html>' },
    { statusCode: 200, body: {} },
    { statusCode: 200, body: { status: 'DOWN' } },
    { statusCode: 503, body: { status: 'UP' } },
    { statusCode: 401, body: {} },
  ]) assert.equal(classifyHealth(response, timestamp).healthy, false);
});

test('transport failures remain unavailable without logging raw error payloads', () => {
  const result = classifyHealth({ error: 'sensitive connection detail' }, timestamp);
  assert.equal(result.status, 'unavailable');
  assert.equal(result.httpCode, null);
  assert.equal(result.checkedAt, timestamp);
  assert.equal(JSON.stringify(result).includes('sensitive connection detail'), false);
});

test('unrecognized health status cannot copy server error text into notifications', () => {
  const result = classifyHealth({ statusCode: 503, body: { status: 'private server error detail' } }, timestamp);
  assert.equal(result.healthy, false);
  assert.equal(result.detail, 'HTTP 503; health status missing/invalid');
  for (const status of ['UP', 'DOWN', 'OUT_OF_SERVICE', 'UNKNOWN']) {
    assert.equal(classifyHealth({ statusCode: 503, body: { status } }, timestamp).detail,
      `HTTP 503; health status ${status}`);
  }
});

test('send once per incident and once on recovery without committing before delivery', () => {
  const healthy = classifyHealth({ statusCode: 200, body: { status: 'UP' } }, timestamp);
  const failed = classifyHealth({ error: 'timeout' }, timestamp);
  const state = {};
  assert.equal(notificationForHealth(healthy, state), null);
  assert.equal(notificationForHealth(failed, state), failed);
  assert.deepEqual(state, {}); // SMTP failure must leave the next attempt eligible.
  state.activeAlert = true; // represents successful SMTP completion
  assert.equal(notificationForHealth(failed, state), null);
  assert.equal(notificationForHealth(healthy, state), healthy);
  assert.deepEqual(state, { activeAlert: true }); // recovery commits after SMTP too
});
