package com.medibridge.core.theme

import androidx.compose.ui.graphics.Color

// ─────────────────────────────────────────────────────────────────────────────
// MediBridge Color Palette
//
// Rules:
//   • Use these tokens everywhere — never hardcode colors in composables.
//   • WCAG AA contrast ratios maintained for text-on-background pairs.
//   • Medical context: trustworthy teal/blue primary, amber/red for alerts.
// ─────────────────────────────────────────────────────────────────────────────

// ── Brand ─────────────────────────────────────────────────────────────────────
/** Main brand teal — used for primary actions, FABs, active nav items */
val TealPrimary       = Color(0xFF0D9488)   // #0D9488 — Teal 600
val TealPrimaryDark   = Color(0xFF0F766E)   // Teal 700 (pressed/dark variant)
val TealContainer     = Color(0xFFCCFBF1)   // Teal 100 (chip backgrounds, light)
val TealContainerDark = Color(0xFF134E4A)   // Teal 900 (chip backgrounds, dark)
val OnTeal            = Color(0xFFFFFFFF)   // Text/icon on teal background

// ── Semantic Status (Medicine Card chips) ─────────────────────────────────────
/** Green — medication verified, no conflicts */
val StatusVerified    = Color(0xFF16A34A)   // Green 600
val StatusVerifiedBg  = Color(0xFFDCFCE7)   // Green 100

/** Amber — needs review or user verification pending */
val StatusReview      = Color(0xFFD97706)   // Amber 600
val StatusReviewBg    = Color(0xFFFEF3C7)   // Amber 100

/** Red — drug conflict detected */
val StatusConflict    = Color(0xFFDC2626)   // Red 600
val StatusConflictBg  = Color(0xFFFEE2E2)   // Red 100

// Dark-mode status variants (higher contrast on dark backgrounds)
val StatusVerifiedDark   = Color(0xFF4ADE80)   // Green 400
val StatusVerifiedBgDark = Color(0xFF14532D)   // Green 900
val StatusReviewDark     = Color(0xFFFBBF24)   // Amber 400
val StatusReviewBgDark   = Color(0xFF78350F)   // Amber 900
val StatusConflictDark   = Color(0xFFF87171)   // Red 400
val StatusConflictBgDark = Color(0xFF7F1D1D)   // Red 900

// ── Light Theme ───────────────────────────────────────────────────────────────
val LightBackground   = Color(0xFFF8FAFC)   // Slate 50 — clean, near-white
val LightSurface      = Color(0xFFFFFFFF)
val LightOnBackground = Color(0xFF0F172A)   // Slate 900 — high-contrast dark text
val LightOnSurface    = Color(0xFF1E293B)   // Slate 800
val LightSurfaceVariant = Color(0xFFF1F5F9) // Slate 100
val LightOutline      = Color(0xFFCBD5E1)   // Slate 300

// ── Dark Theme ────────────────────────────────────────────────────────────────
val DarkBackground    = Color(0xFF121212)   // Material dark baseline
val DarkSurface       = Color(0xFF1E1E1E)   // Slightly lifted card surface
val DarkSurfaceVariant= Color(0xFF2A2A2A)
val DarkOnBackground  = Color(0xFFE0E0E0)   // Off-white — WCAG AA on #121212
val DarkOnSurface     = Color(0xFFCFD8DC)
val DarkOutline       = Color(0xFF37474F)

// ── Gradient stops (used in HomeScreen header decoration) ─────────────────────
val GradientStart     = Color(0xFF0D9488)
val GradientEnd       = Color(0xFF0EA5E9)   // Sky 500 — subtle teal→blue sweep
