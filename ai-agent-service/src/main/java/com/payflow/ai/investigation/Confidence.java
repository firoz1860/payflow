package com.payflow.ai.investigation;

/**
 * Deterministic confidence for an investigation, decided by code from which
 * authoritative tools returned evidence — never by the LLM.
 * <ul>
 *   <li>{@code HIGH} — all key facts present from authoritative tools;</li>
 *   <li>{@code MEDIUM} — some key facts present;</li>
 *   <li>{@code LOW} — insufficient (e.g. payment only);</li>
 *   <li>{@code UNKNOWN} — no authoritative evidence at all.</li>
 * </ul>
 */
public enum Confidence {
    HIGH,
    MEDIUM,
    LOW,
    UNKNOWN
}
