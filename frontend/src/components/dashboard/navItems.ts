import {
  Activity,
  BarChart3,
  BookOpen,
  Code2,
  CreditCard,
  KeyRound,
  LayoutDashboard,
  type LucideIcon,
  QrCode,
  Receipt,
  Settings,
  ShieldCheck,
  Sparkles,
  Users,
  Webhook,
} from 'lucide-react';

/**
 * Single source of truth for authenticated-dashboard navigation.
 *
 * Every route and permission here mirrors the real PayFlow routes defined in
 * App.tsx and the access rules previously enforced by the sidebar — nothing is
 * invented. Both the top navigation and the command menu consume this module so
 * they can never drift apart.
 */

export interface NavLeaf {
  to: string;
  label: string;
  icon: LucideIcon;
  /** Required permission, or null/undefined when always visible. */
  perm?: string | null;
  /** Short description, shown in dropdowns and the command menu. */
  hint?: string;
}

export interface NavGroup {
  kind: 'group';
  label: string;
  icon: LucideIcon;
  /** Path prefixes that mark this group (and its trigger) active. */
  match: string[];
  adminOnly?: boolean;
  children: NavLeaf[];
}

export interface NavLeafNode extends NavLeaf {
  kind: 'leaf';
  /** Exact-match only (e.g. Overview), otherwise prefix match is used. */
  exact?: boolean;
}

export type NavNode = NavLeafNode | NavGroup;

export const NAV: NavNode[] = [
  { kind: 'leaf', to: '/dashboard', label: 'Overview', icon: LayoutDashboard, exact: true },
  {
    kind: 'group',
    label: 'Payments',
    icon: CreditCard,
    match: ['/payments'],
    children: [
      { to: '/payments', label: 'All payments', icon: CreditCard, perm: 'payments:read', hint: 'Browse and search payments' },
      { to: '/payments/create', label: 'Create payment', icon: Receipt, perm: 'payments:create', hint: 'Start a new payment' },
      { to: '/payments/qr', label: 'QR payments', icon: QrCode, perm: 'payments:create', hint: 'Generate a QR checkout' },
    ],
  },
  { kind: 'leaf', to: '/ledger', label: 'Ledger', icon: BookOpen, perm: 'ledger:read' },
  { kind: 'leaf', to: '/analytics', label: 'Analytics', icon: BarChart3, perm: 'payments:read' },
  {
    kind: 'group',
    label: 'Developers',
    icon: Code2,
    match: ['/api-keys', '/webhooks', '/developers'],
    children: [
      { to: '/api-keys', label: 'API keys', icon: KeyRound, perm: 'api_keys:manage', hint: 'Manage secret & publishable keys' },
      { to: '/webhooks', label: 'Webhooks', icon: Webhook, perm: 'webhooks:manage', hint: 'Endpoints & delivery' },
      { to: '/developers', label: 'Documentation', icon: BookOpen, hint: 'API reference & guides' },
    ],
  },
  { kind: 'leaf', to: '/ai', label: 'Copilot', icon: Sparkles, perm: 'ai:use' },
  {
    kind: 'group',
    label: 'Admin',
    icon: ShieldCheck,
    match: ['/monitoring', '/admin'],
    adminOnly: true,
    children: [
      { to: '/monitoring', label: 'Monitoring', icon: Activity, perm: 'platform:admin', hint: 'Platform health & events' },
      { to: '/admin/merchants', label: 'Merchants', icon: ShieldCheck, perm: 'platform:admin', hint: 'All merchant accounts' },
      { to: '/admin/roles', label: 'User roles', icon: Users, perm: 'platform:admin', hint: 'Roles & permissions' },
    ],
  },
];

/** Account-menu destinations (both are real App.tsx routes). */
export const ACCOUNT_LINKS: NavLeaf[] = [
  { to: '/merchant', label: 'Merchant profile', icon: ShieldCheck, perm: 'merchant:read' },
  { to: '/settings', label: 'Settings', icon: Settings, perm: 'merchant:read' },
];

type Can = (perm?: string | null) => boolean;

function leafVisible(leaf: NavLeaf, can: Can): boolean {
  return !leaf.perm || can(leaf.perm);
}

/** Returns the nav tree filtered to what the current user may access. */
export function getVisibleNav(can: Can, isAdmin: boolean): NavNode[] {
  const out: NavNode[] = [];
  for (const node of NAV) {
    if (node.kind === 'leaf') {
      if (leafVisible(node, can)) out.push(node);
      continue;
    }
    if (node.adminOnly && !isAdmin) continue;
    const children = node.children.filter((c) => leafVisible(c, can));
    if (children.length) out.push({ ...node, children });
  }
  return out;
}

/** Flat list of every reachable destination, for the command menu. */
export interface CommandDestination extends NavLeaf {
  group: string;
}

export function getCommandDestinations(can: Can, isAdmin: boolean): CommandDestination[] {
  const out: CommandDestination[] = [];
  for (const node of getVisibleNav(can, isAdmin)) {
    if (node.kind === 'leaf') {
      out.push({ ...node, group: 'Navigate' });
    } else {
      for (const child of node.children) out.push({ ...child, group: node.label });
    }
  }
  for (const link of ACCOUNT_LINKS) {
    if (leafVisible(link, can)) out.push({ ...link, group: 'Account' });
  }
  return out;
}

/** True when `pathname` should light up a node. */
export function isNodeActive(node: NavNode, pathname: string): boolean {
  if (node.kind === 'group') return node.match.some((m) => pathname === m || pathname.startsWith(m + '/'));
  if (node.exact) return pathname === node.to;
  return pathname === node.to || pathname.startsWith(node.to + '/');
}
