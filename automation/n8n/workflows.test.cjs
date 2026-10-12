const { test } = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');

for (const name of ['payflow-health-alert', 'payflow-daily-summary', 'payflow-email-smoke-test']) {
  test(`${name} has connected inactive nodes without exported credentials`, () => {
    const workflow = JSON.parse(fs.readFileSync(path.join(__dirname, `${name}.json`)));
    assert.equal(workflow.active, false);
    const names = new Set(workflow.nodes.map(node => node.name));
    assert.equal(names.size, workflow.nodes.length);
    for (const [source, outputs] of Object.entries(workflow.connections)) {
      assert.ok(names.has(source));
      for (const connections of outputs.main) {
        for (const edge of connections) assert.ok(names.has(edge.node));
      }
    }
    for (const node of workflow.nodes) assert.equal(node.credentials, undefined);
    const send = workflow.nodes.find(node => node.type === 'n8n-nodes-base.emailSend');
    assert.equal(send.parameters.toEmail, '={{ $vars.PAYFLOW_ALERT_EMAIL }}');
    assert.equal(send.parameters.emailFormat, 'text');
    assert.equal(send.continueOnFail, undefined);
  });
}

test('healthy gateway ends incident workflow before email; failure does not commit state', () => {
  const workflow = JSON.parse(fs.readFileSync(path.join(__dirname, 'payflow-health-alert.json')));
  const code = workflow.nodes.find(node => node.name === 'Classify Gateway Health').parameters.jsCode;
  const run = new Function('$input', '$getWorkflowStaticData', code);
  const state = {};
  assert.deepEqual(run({ first: () => ({ json: { statusCode: 200, body: { status: 'UP' } } }) }, () => state), []);
  const failed = run({ first: () => ({ json: { error: 'timeout' } }) }, () => state);
  assert.equal(failed.length, 1);
  assert.equal(failed[0].json.status, 'unavailable');
  assert.deepEqual(state, {});
  const record = workflow.nodes.find(node => node.name === 'Record Delivered Alert').parameters.jsCode;
  const commit = new Function('$input', '$getWorkflowStaticData', '$', record);
  commit({ all: () => [] }, () => state, () => ({ first: () => failed[0] }));
  assert.equal(state.activeAlert, true);
  assert.deepEqual(run({ first: () => ({ json: { error: 'timeout' } }) }, () => state), []);
  const recovery = run({ first: () => ({ json: { statusCode: 200, body: { status: 'UP' } } }) }, () => state);
  assert.equal(recovery.length, 1);
  assert.equal(recovery[0].json.status, 'healthy');
});

test('generated notification workflows do not relay arbitrary health response text', () => {
  for (const name of ['payflow-health-alert', 'payflow-daily-summary']) {
    const workflow = JSON.parse(fs.readFileSync(path.join(__dirname, `${name}.json`)));
    const code = workflow.nodes.find(node => node.name === 'Classify Gateway Health').parameters.jsCode;
    const run = new Function('$input', '$getWorkflowStaticData', code);
    const response = { statusCode: 503, body: { status: 'private server error detail' } };
    const result = run({ first: () => ({ json: response }) }, () => ({}));
    assert.equal(result[0].json.detail, 'HTTP 503; health status missing/invalid');
  }
});
