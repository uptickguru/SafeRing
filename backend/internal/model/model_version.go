package model

import "time"

// ModelVersion tracks a deployed ML model for number/SMS classification.
type ModelVersion struct {
	// ID is the auto-increment primary key.
	ID int64 `json:"id" db:"id"`

	// Version is a semantic version string (e.g., "1.2.3").
	Version string `json:"version" db:"version"`

	// ModelType indicates the model purpose: "number" or "sms".
	ModelType string `json:"model_type" db:"model_type"`

	// FilePath is the path to the model binary on disk.
	FilePath string `json:"-" db:"file_path"`

	// DownloadURL is the public URL for clients to download the model.
	DownloadURL string `json:"download_url,omitempty" db:"download_url"`

	// SHA256 is the hex-encoded SHA-256 checksum of the model file.
	SHA256 string `json:"sha256" db:"sha256"`

	// FileSizeBytes is the size of the model file in bytes.
	FileSizeBytes int64 `json:"file_size_bytes,omitempty" db:"file_size_bytes"`

	// Accuracy is the model's validation accuracy (if available).
	Accuracy *float64 `json:"accuracy,omitempty" db:"accuracy"`

	// IsActive indicates if this is the currently deployed version.
	IsActive bool `json:"is_active" db:"is_active"`

	// CreatedAt is when this model version was created.
	CreatedAt time.Time `json:"created_at" db:"created_at"`
}

// ModelLatestResponse is the JSON response for GET /v1/model/latest.
type ModelLatestResponse struct {
	Version     string `json:"version"`
	DownloadURL string `json:"download_url"`
	SHA256      string `json:"sha256"`
	FileSize    int64  `json:"file_size_bytes,omitempty"`
	ModelType   string `json:"model_type"`
	Accuracy    *float64 `json:"accuracy,omitempty"`
}
