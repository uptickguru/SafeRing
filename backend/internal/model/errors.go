package model

import "errors"

// Sentinel errors for validation and business logic.
var (
	ErrMissingHash  = errors.New("missing required field: hash")
	ErrInvalidHash  = errors.New("invalid hash: must be a 64-character hex string (SHA-256)")
	ErrInvalidTag   = errors.New("invalid tag: must be one of: irs, tech-support, grandparent, romance, phishing, robocall, spoofed, medicare, other")
	ErrNotFound     = errors.New("record not found")
	ErrConflict     = errors.New("record already exists")
	ErrInternal     = errors.New("internal server error")
	ErrRateLimited  = errors.New("rate limit exceeded")
)
