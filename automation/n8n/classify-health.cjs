// Public gateway health only. This is not a payment/ledger readiness assertion.
function classifyHealth(response, checkedAt) {
  const httpCode = Number.isInteger(response.statusCode) ? response.statusCode : null;
  const bodyStatus = typeof response.body?.status === 'string' ? response.body.status : null;
  const healthy = httpCode !== null && httpCode >= 200 && httpCode < 300 && bodyStatus === 'UP';
  return {
    healthy,
    status: healthy ? 'healthy' : httpCode === null ? 'unavailable' : 'unhealthy',
    httpCode,
    detail: httpCode === null ? 'Gateway health request failed or timed out'
      : `HTTP ${httpCode}; health status ${bodyStatus || 'missing/invalid'}`,
    checkedAt,
    scope: 'public gateway health; other services and payment processing are not verified',
  };
}

// State is committed only by the downstream node after SMTP succeeds.
function notificationForHealth(result, state) {
  if (result.healthy && !state.activeAlert) return null;
  if (!result.healthy && state.activeAlert) return null;
  return result;
}

module.exports = { classifyHealth, notificationForHealth };
