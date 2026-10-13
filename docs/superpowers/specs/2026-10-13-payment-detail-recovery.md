# Payment detail recovery

Service failures must not claim a payment is missing. A confirmed HTTP 404
may show the existing missing-record state; all other read failures need a
persistent error and an explicit read-only retry. Hide payment actions while
its status or identity is unconfirmed. Do not replay a failed cancellation.

Reads, polling and cancellation responses for a previous route must not
replace the current payment. A manual refresh must supersede an older poll.
Cancellation must submit once per in-flight action, target the displayed
payment, and require the existing `payments:create` permission. An uncertain
cancellation result requires a status read before another action.

Preserve the 60-second polling window, five-second interval, existing TEST
checkout behavior and backend contracts. No dependencies, migrations,
credentials, provider configuration or live financial actions change.
