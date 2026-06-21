// Package ml provides model versioning, metadata, and training pipeline infrastructure
// for SafeRing's classification models.
//
// Number Classification Model:
//   - Input: Phone number hash + prefix features
//   - Features: Number age, prefix region, associated scam types, user report count
//   - Output: Risk score 0.0-1.0 + scam type tags
//
// SMS Text Classification Model (on-device):
//   - DistilBERT/MobileBERT quantized to TFLite/CoreML
//   - Classes: Legitimate, Spam, Scam (IRS, Tech Support, Phishing, Romance, etc.)
//   - On-device inference only
package ml

import (
	"context"
	"crypto/sha256"
	"encoding/hex"
	"encoding/json"
	"fmt"
	"os"
	"path/filepath"
	"time"

	"go.uber.org/zap"

	"github.com/safering/backend/internal/model"
	"github.com/safering/backend/internal/store"
)

// Pipeline manages the ML model lifecycle: versioning, deployment, and retraining.
type Pipeline struct {
	modelStore   *store.ModelStore
	numberStore  *store.ScamNumberStore
	reportStore  *store.ScamReportStore
	modelDir     string
	trainInterval time.Duration
	logger       *zap.Logger
}

// NewPipeline creates a new ML pipeline manager.
func NewPipeline(
	modelStore *store.ModelStore,
	numberStore *store.ScamNumberStore,
	reportStore *store.ScamReportStore,
	modelDir string,
	trainInterval time.Duration,
	logger *zap.Logger,
) *Pipeline {
	return &Pipeline{
		modelStore:    modelStore,
		numberStore:   numberStore,
		reportStore:   reportStore,
		modelDir:      modelDir,
		trainInterval: trainInterval,
		logger:        logger.Named("ml.pipeline"),
	}
}

// GetLatestModel returns the latest active model for the given type.
func (p *Pipeline) GetLatestModel(ctx context.Context, modelType string) (*model.ModelVersion, error) {
	return p.modelStore.GetActiveByType(ctx, modelType)
}

// GetModelVersion returns a specific model version.
func (p *Pipeline) GetModelVersion(ctx context.Context, version, modelType string) (*model.ModelVersion, error) {
	return p.modelStore.GetByVersion(ctx, version, modelType)
}

// ModelInfo returns metadata about a model version.
// This is the data served by the /v1/model endpoint.
type ModelInfo struct {
	Version       string    `json:"version"`
	ModelType     string    `json:"model_type"`
	FileSizeBytes int64     `json:"file_size_bytes,omitempty"`
	SHA256        string    `json:"sha256"`
	Accuracy      *float64  `json:"accuracy,omitempty"`
	TrainingDate  time.Time `json:"training_date,omitempty"`
	Features      []string  `json:"features,omitempty"`
}

// ModelManifest stores model metadata alongside the binary.
type ModelManifest struct {
	Version      string    `json:"version"`
	ModelType    string    `json:"model_type"`
	CreatedAt    time.Time `json:"created_at"`
	SHA256       string    `json:"sha256"`
	FileSize     int64     `json:"file_size"`
	Accuracy     *float64  `json:"accuracy,omitempty"`
	FeatureCount int       `json:"feature_count,omitempty"`
}

// RegisterModel registers a trained model file with the system.
func (p *Pipeline) RegisterModel(ctx context.Context, filePath, modelType string, accuracy *float64) (*model.ModelVersion, error) {
	// Compute SHA-256 of model file
	data, err := os.ReadFile(filePath)
	if err != nil {
		return nil, fmt.Errorf("read model file: %w", err)
	}

	hash := sha256.Sum256(data)
	sha256Hex := hex.EncodeToString(hash[:])

	fileInfo, err := os.Stat(filePath)
	if err != nil {
		return nil, fmt.Errorf("stat model file: %w", err)
	}

	// Generate version string from timestamp
	version := fmt.Sprintf("1.%d", time.Now().Unix())

	mv := &model.ModelVersion{
		Version:       version,
		ModelType:     modelType,
		FilePath:      filePath,
		DownloadURL:   fmt.Sprintf("/v1/model?type=%s&version=%s", modelType, version),
		SHA256:        sha256Hex,
		FileSizeBytes: fileInfo.Size(),
		Accuracy:      accuracy,
		IsActive:      false,
		CreatedAt:     time.Now(),
	}

	if err := p.modelStore.Insert(ctx, mv); err != nil {
		return nil, fmt.Errorf("insert model version: %w", err)
	}

	p.logger.Info("registered new model",
		zap.String("version", version),
		zap.String("type", modelType),
		zap.Int64("size", fileInfo.Size()),
	)

	return mv, nil
}

// DeployModel sets a model version as the active deployment.
func (p *Pipeline) DeployModel(ctx context.Context, version, modelType string) error {
	mv, err := p.modelStore.GetByVersion(ctx, version, modelType)
	if err != nil {
		return fmt.Errorf("get model version: %w", err)
	}
	return p.modelStore.SetActive(ctx, mv.ID, modelType)
}

// ReadManifest loads a model manifest from disk.
func ReadManifest(modelDir, modelType string) (*ModelManifest, error) {
	manifestPath := filepath.Join(modelDir, modelType+"_manifest.json")
	data, err := os.ReadFile(manifestPath)
	if err != nil {
		return nil, fmt.Errorf("read manifest: %w", err)
	}
	var manifest ModelManifest
	if err := json.Unmarshal(data, &manifest); err != nil {
		return nil, fmt.Errorf("parse manifest: %w", err)
	}
	return &manifest, nil
}

// WriteManifest writes a model manifest to disk.
func WriteManifest(modelDir, modelType string, manifest *ModelManifest) error {
	if err := os.MkdirAll(modelDir, 0755); err != nil {
		return fmt.Errorf("create model dir: %w", err)
	}
	manifestPath := filepath.Join(modelDir, modelType+"_manifest.json")
	data, err := json.MarshalIndent(manifest, "", "  ")
	if err != nil {
		return fmt.Errorf("marshal manifest: %w", err)
	}
	return os.WriteFile(manifestPath, data, 0644)
}
