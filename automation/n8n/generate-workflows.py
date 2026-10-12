"""Generate inactive import templates; never include credentials or recipient PII."""
import json
from pathlib import Path

root = Path(__file__).resolve().parent
classifier = (root / 'classify-health.cjs').read_text().split('module.exports')[0]

def node(name, kind, version, parameters, x):
    return {'id': name.lower().replace(' ', '-'), 'name': name,
            'type': 'n8n-nodes-base.' + kind, 'typeVersion': version,
            'position': [x, 0], 'parameters': parameters}

def email(subject, text, x):
    return node('Send Email', 'emailSend', 2.1, {
        'operation': 'send', 'fromEmail': '={{ $vars.PAYFLOW_EMAIL_FROM }}',
        'toEmail': '={{ $vars.PAYFLOW_ALERT_EMAIL }}', 'subject': subject,
        'emailFormat': 'text', 'text': text, 'options': {'appendAttribution': False}}, x)

def write(name, nodes):
    connections = {a['name']: {'main': [[{'node': b['name'], 'type': 'main', 'index': 0}]]}
                   for a, b in zip(nodes, nodes[1:])}
    workflow = {'name': name, 'active': False, 'nodes': nodes, 'connections': connections,
                'settings': {'executionOrder': 'v1', 'timezone': 'UTC'}, 'pinData': {}}
    (root / (name + '.json')).write_text(json.dumps(workflow, indent=2) + '\n')

write('payflow-email-smoke-test', [node('Manual Test', 'manualTrigger', 1, {}, 0),
      email('PayFlow email connection test',
            'PayFlow operations email connection test. This is a test, not an incident alert.', 240)])

for mode in ['health-alert', 'daily-summary']:
    rule = ({'field': 'minutes', 'minutesInterval': 15} if mode == 'health-alert'
            else {'field': 'cronExpression', 'expression': '0 8 * * *'})
    trigger = node('Schedule', 'scheduleTrigger', 1.2, {'rule': {'interval': [rule]}}, 0)
    request = node('Read Gateway Health', 'httpRequest', 4.2, {
        'url': "={{ ($vars.PAYFLOW_BASE_URL || 'https://payflow.unconfigured.invalid').replace(/\\/$/, '') + '/actuator/health' }}",
        'options': {'timeout': 10000, 'response': {'response': {
            'fullResponse': True, 'neverError': True, 'responseFormat': 'json'}}}}, 240)
    request['onError'] = 'continueRegularOutput'
    code = classifier + '\nconst result = classifyHealth($input.first().json, new Date().toISOString());\n'
    if mode == 'health-alert':
        code += "const state = $getWorkflowStaticData('global');\nif (!notificationForHealth(result, state)) return [];\n"
    code += 'return [{ json: result }];\n'
    classify = node('Classify Gateway Health', 'code', 2, {'jsCode': code}, 480)
    subject = ('={{ "PayFlow gateway alert: " + $json.status }}' if mode == 'health-alert'
               else 'PayFlow daily gateway health summary')
    send = email(subject, '={{ $json.detail + "\\nChecked: " + $json.checkedAt + "\\nScope: " + $json.scope }}', 720)
    nodes = [trigger, request, classify, send]
    if mode == 'health-alert':
        nodes.append(node('Record Delivered Alert', 'code', 2, {'jsCode':
            "const state = $getWorkflowStaticData('global');\n"
            "state.activeAlert = !$('Classify Gateway Health').first().json.healthy;\n"
            "return $input.all();"}, 960))
    write('payflow-' + mode, nodes)
