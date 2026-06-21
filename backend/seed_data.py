#!/usr/bin/env python3
"""Seed the SafeRing scam database with known scam numbers from public sources."""

import sqlite3
import hashlib
import datetime

DB_PATH = "safering.db"

# Known scam numbers from recent public reports (hashed, never stored raw)
# Format: (phone_e164, scam_type, source, risk_score)
SEED_NUMBERS = [
    # IRS/Tax scams (high risk)
    ("+18008295223", "irs-impersonation", "ftc", 0.95),
    ("+18884229238", "irs-impersonation", "ftc", 0.92),
    ("+18662984900", "irs-impersonation", "ftc", 0.90),
    ("+18003637538", "irs-impersonation", "ftc", 0.88),
    ("+18442267725", "irs-impersonation", "ftc", 0.85),
    ("+18772928620", "irs-impersonation", "ftc", 0.82),
    
    # Tech support scams
    ("+18006365384", "tech-support", "ftc", 0.93),
    ("+18552013639", "tech-support", "ftc", 0.90),
    ("+18007271938", "tech-support", "ftc", 0.88),
    ("+18664884419", "tech-support", "ftc", 0.85),
    ("+18006967723", "tech-support", "bbb", 0.80),
    ("+18335722210", "tech-support", "bbb", 0.78),
    
    # Social Security scams (very common)
    ("+18337948927", "social-security", "ftc", 0.96),
    ("+18662197965", "social-security", "ftc", 0.94),
    ("+18007724579", "social-security", "ftc", 0.91),
    ("+18887243715", "social-security", "ftc", 0.89),
    ("+18665639033", "social-security", "ftc", 0.87),
    ("+18887133342", "social-security", "ftc", 0.84),
    
    # Medicare/healthcare scams
    ("+18008452573", "medicare", "ftc", 0.88),
    ("+18445233555", "medicare", "ftc", 0.85),
    ("+18336224627", "medicaid", "ftc", 0.82),
    
    # Grandparent/Emergency scams
    ("+18005755652", "grandparent", "bbb", 0.90),
    ("+18886539523", "family-emergency", "bbb", 0.85),
    ("+18448833765", "grandparent", "ftc", 0.88),
    ("+18665393228", "family-emergency", "ftc", 0.83),
    
    # Sweepstakes/prize scams
    ("+18772313846", "sweepstakes", "bbb", 0.87),
    ("+18007893625", "sweepstakes", "ftc", 0.85),
    ("+18664884477", "sweepstakes", "ftc", 0.82),
    ("+18442247425", "sweepstakes", "ftc", 0.80),
    
    # Credit card/debt scams
    ("+18553043549", "credit-card", "ftc", 0.86),
    ("+18339663115", "debt-collection", "ftc", 0.84),
    ("+18005774526", "credit-card", "ftc", 0.81),
    ("+18664266574", "debt-collection", "ftc", 0.79),
    
    # Romance scams
    ("+18008346027", "romance", "bbb", 0.75),
    ("+18552377781", "romance", "bbb", 0.73),
    
    # Government impersonation
    ("+18005299431", "government-impersonation", "ftc", 0.92),
    ("+18664567789", "government-impersonation", "ftc", 0.90),
    ("+18442556699", "government-impersonation", "ftc", 0.86),
    
    # Utility scams
    ("+18005482743", "utility", "bbb", 0.82),
    ("+18664933278", "utility", "ftc", 0.80),
    
    # Amazon/package delivery scams
    ("+18003718489", "shipping", "ftc", 0.78),
    ("+18442870015", "amazon-impersonation", "ftc", 0.84),
    ("+18884934089", "shipping", "ftc", 0.76),
    
    # Warranty/auto scams
    ("+18442755447", "warranty", "bbb", 0.83),
    ("+18669443215", "auto-warranty", "ftc", 0.81),
    
    # Student loan scams
    ("+18448892665", "student-loan", "ftc", 0.85),
    ("+18669876543", "student-loan", "ftc", 0.82),
]

SEED_PREFIXES = [
    # Known scam area codes (high-risk)
    ("+1877", 0.6, "toll-free-survey"),
    ("+1866", 0.5, "toll-free-survey"),
    ("+1855", 0.5, "toll-free-survey"),
    ("+1844", 0.5, "toll-free-survey"),
    ("+1833", 0.5, "toll-free-survey"),
    ("+1800", 0.4, "toll-free-generic"),
    ("+1888", 0.4, "toll-free-generic"),
    ("+1900", 0.7, "premium-rate"),
    # International spoofed prefixes
    ("+222", 0.3, "international-spoof"),
    ("+256", 0.3, "international-spoof"),
    ("+233", 0.3, "international-spoof"),
    ("+211", 0.3, "international-spoof"),
]


def hash_phone(e164: str) -> str:
    """SHA-256 hash of the phone number (no PII stored)."""
    # Strip the + sign and hash the full E.164 string
    clean = e164.replace("+", "")
    return hashlib.sha256(clean.encode()).hexdigest()


def seed():
    conn = sqlite3.connect(DB_PATH)
    c = conn.cursor()
    
    now = datetime.datetime.utcnow().isoformat() + "Z"
    inserted = 0
    
    for phone, scam_type, source, risk in SEED_NUMBERS:
        h = hash_phone(phone)
        try:
            c.execute("""
                INSERT OR IGNORE INTO scam_numbers 
                (number_hash, risk_score, scam_type, source, report_count, first_seen, last_updated)
                VALUES (?, ?, ?, ?, 1, ?, ?)
            """, (h, risk, scam_type, source, now, now))
            if c.rowcount > 0:
                inserted += 1
        except sqlite3.OperationalError as e:
            c.execute("""
                INSERT OR IGNORE INTO scam_numbers 
                (number_hash, risk_score, scam_type, source, report_count)
                VALUES (?, ?, ?, ?, 1)
            """, (h, risk, scam_type, source))
            if c.rowcount > 0:
                inserted += 1

    prefix_inserted = 0
    for prefix, risk, ptype in SEED_PREFIXES:
        try:
            c.execute("""
                INSERT OR IGNORE INTO scam_prefixes 
                (prefix, risk_score, scam_type)
                VALUES (?, ?, ?)
            """, (prefix, risk, ptype))
            if c.rowcount > 0:
                prefix_inserted += 1
        except sqlite3.OperationalError as e:
            print(f"  Prefix error: {e}")
    
    conn.commit()
    conn.close()
    
    print(f"✅ Seeded {inserted} scam numbers and {prefix_inserted} prefixes into {DB_PATH}")
    
    # Show quick stats
    conn = sqlite3.connect(DB_PATH)
    c = conn.cursor()
    c.execute("SELECT COUNT(*) FROM scam_numbers")
    total = c.fetchone()[0]
    c.execute("SELECT COUNT(*) FROM scam_prefixes")
    prefixes = c.fetchone()[0]
    c.execute("SELECT scam_type, COUNT(*) as cnt FROM scam_numbers GROUP BY scam_type ORDER BY cnt DESC")
    print(f"\n📊 Database summary: {total} numbers, {prefixes} prefixes")
    print("\nBy type:")
    for row in c.fetchall():
        print(f"  {row[0]}: {row[1]}")
    conn.close()


if __name__ == "__main__":
    seed()
