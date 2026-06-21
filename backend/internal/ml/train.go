package ml

import (
	"context"
	"encoding/json"
	"fmt"
	"math"
	"os"
	"path/filepath"
	"time"

	"go.uber.org/zap"

	"github.com/safering/backend/internal/model"
	"github.com/safering/backend/internal/store"
)

// Trainer implements the number classifier training pipeline.
// It uses statistical features from the database to produce risk models.
//
// The SafeRing number classifier is a lightweight model that runs on-device.
// Training produces feature weights that the client uses for inference.
// This is NOT a deep learning pipeline — it produces a small feature vector
// and weights file (~100KB) suitable for mobile deployment.
//
// Features used for number classification:
//   - NumberAgeDays: How long since this hash first appeared
//   - ReportCount: Total user reports
//   - SourceWeight: Credibility weight of the source(s)
//   - ScamTypeRisk: Baseline risk associated with the scam type
//   - PrefixRisk: Aggregate risk of the number's prefix group
//   - RecencyScore: How recently the number was reported (decay function)
type Trainer struct {
	numberStore *store.ScamNumberStore
	prefixStore *store.ScamPrefixStore
	reportStore *store.ScamReportStore
	modelDir    string
	logger      *zap.Logger
}

// NewTrainer creates a new ML trainer.
func NewTrainer(
	numberStore *store.ScamNumberStore,
	prefixStore *store.ScamPrefixStore,
	reportStore *store.ScamReportStore,
	modelDir string,
	logger *zap.Logger,
) *Trainer {
	return &Trainer{
		numberStore: numberStore,
		prefixStore: prefixStore,
		reportStore: reportStore,
		modelDir:    modelDir,
		logger:      logger.Named("ml.trainer"),
	}
}

// TrainingResult holds the output of a training run.
type TrainingResult struct {
	ModelVersion string        `json:"model_version"`
	ModelType    string        `json:"model_type"`
	Features     []string      `json:"features"`
	Weights      []float64     `json:"weights"`
	Accuracy     float64       `json:"accuracy"`
	Samples      int           `json:"samples"`
	Duration     time.Duration `json:"duration"`
}

// TrainNumberModel trains the number classification model from stored data.
func (t *Trainer) TrainNumberModel(ctx context.Context) (*TrainingResult, error) {
	t.logger.Info("starting number model training")
	start := time.Now()

	// Fetch training data
	highRisk, err := t.numberStore.GetHighRisk(ctx, 0.3, 1000)
	if err != nil {
		return nil, fmt.Errorf("fetch training data: %w", err)
	}

	if len(highRisk) < 10 {
		t.logger.Warn("insufficient training data", zap.Int("samples", len(highRisk)))
		return nil, fmt.Errorf("need at least 10 scam numbers to train, got %d", len(highRisk))
	}

	prefixes, err := t.prefixStore.GetHighRisk(ctx, 0.1)
	if err != nil {
		return nil, fmt.Errorf("fetch prefix data: %w", err)
	}

	// Build prefix risk lookup
	prefixRisk := make(map[string]float64)
	for _, p := range prefixes {
		if len(p.Prefix) >= 10 {
			prefixRisk[p.Prefix] = p.RiskScore
		}
	}

	// Feature engineering
	features := extractFeatures(highRisk, prefixRisk)

	// Train model (logistic regression)
	weights := trainLogisticRegression(features)

	// Calculate accuracy on training data
	accuracy := evaluateModel(highRisk, features, weights)

	result := &TrainingResult{
		ModelType: "number",
		Features:  featureNames(),
		Weights:   weights,
		Accuracy:  accuracy,
		Samples:   len(highRisk),
		Duration:  time.Since(start),
	}

	// Generate version
	result.ModelVersion = fmt.Sprintf("1.%d", time.Now().Unix())

	// Save model to disk
	if err := t.saveModel(result); err != nil {
		return nil, fmt.Errorf("save model: %w", err)
	}

	t.logger.Info("number model training complete",
		zap.Int("samples", result.Samples),
		zap.Float64("accuracy", result.Accuracy),
		zap.Duration("duration", result.Duration),
	)

	return result, nil
}

// ModelFeatures holds pre-computed features for a single sample.
type ModelFeatures struct {
	NumberAgeDays  float64
	LogReportCount float64
	SourceWeight   float64
	ScamTypeRisk   float64
	PrefixRisk     float64
	RecencyScore   float64
}

// featureNames returns the ordered list of feature names.
func featureNames() []string {
	return []string{
		"number_age_days",
		"log_report_count",
		"source_weight",
		"scam_type_risk",
		"prefix_risk",
		"recency_score",
	}
}

// extractFeatures computes feature vectors for all training samples.
func extractFeatures(numbers []*model.ScamNumber, prefixRisk map[string]float64) []ModelFeatures {
	features := make([]ModelFeatures, len(numbers))
	now := time.Now()

	for i, sn := range numbers {
		f := ModelFeatures{}

		// 1. Number age (days since first seen), capped at 365
		age := now.Sub(sn.FirstSeen).Hours() / 24
		if age < 0 {
			age = 0
		}
		f.NumberAgeDays = math.Min(age, 365) / 365.0

		// 2. Log of report count (diminishing returns)
		if sn.ReportCount > 0 {
			f.LogReportCount = math.Log1p(float64(sn.ReportCount)) / 10.0
		}

		// 3. Source weight (credibility of the source)
		f.SourceWeight = sourceCredibility(sn.Source)

		// 4. Scam type baseline risk
		f.ScamTypeRisk = scamTypeBaseline(sn.ScamType)

		// 5. Prefix risk (if available)
		if len(sn.NumberHash) >= 10 {
			prefix := sn.NumberHash[:10]
			if risk, ok := prefixRisk[prefix]; ok {
				f.PrefixRisk = risk
			}
		}

		// 6. Recency score (decay over time)
		daysSinceUpdate := now.Sub(sn.LastUpdated).Hours() / 24
		if daysSinceUpdate < 0 {
			daysSinceUpdate = 0
		}
		f.RecencyScore = math.Exp(-daysSinceUpdate / 30.0)

		features[i] = f
	}

	return features
}

// trainLogisticRegression performs gradient descent to learn feature weights.
func trainLogisticRegression(features []ModelFeatures) []float64 {
	n := len(featureNames())
	weights := make([]float64, n)

	// Initialize with small values
	for i := range weights {
		weights[i] = 0.1
	}

	learningRate := 0.01
	epochs := 100
	l2Lambda := 0.001

	for epoch := 0; epoch < epochs; epoch++ {
		gradients := make([]float64, n)

		for _, f := range features {
			prediction := predictProbability(f, weights)
			target := f.ScamTypeRisk
			error := prediction - target

			gradients[0] += error * f.NumberAgeDays
			gradients[1] += error * f.LogReportCount
			gradients[2] += error * f.SourceWeight
			gradients[3] += error * f.ScamTypeRisk
			gradients[4] += error * f.PrefixRisk
			gradients[5] += error * f.RecencyScore
		}

		for i := range weights {
			weights[i] -= learningRate * (gradients[i]/float64(len(features)) + l2Lambda*weights[i])
		}
	}

	return weights
}

// predictProbability computes sigmoid(w·x) for given features and weights.
func predictProbability(f ModelFeatures, weights []float64) float64 {
	z := weights[0]*f.NumberAgeDays +
		weights[1]*f.LogReportCount +
		weights[2]*f.SourceWeight +
		weights[3]*f.ScamTypeRisk +
		weights[4]*f.PrefixRisk +
		weights[5]*f.RecencyScore

	return 1.0 / (1.0 + math.Exp(-z))
}

// evaluateModel computes accuracy on training data.
func evaluateModel(numbers []*model.ScamNumber, features []ModelFeatures, weights []float64) float64 {
	if len(numbers) == 0 {
		return 0
	}

	correct := 0
	for i := range numbers {
		predicted := predictProbability(features[i], weights)
		actual := numbers[i].RiskScore

		if math.Abs(predicted-actual) < 0.2 {
			correct++
		}
	}

	return float64(correct) / float64(len(numbers))
}

// sourceCredibility returns a weight for the data source's credibility.
func sourceCredibility(source string) float64 {
	switch source {
	case "user_report":
		return 0.8
	case "bbb":
		return 0.75
	case "ftc":
		return 0.7
	case "reddit":
		return 0.4
	default:
		return 0.5
	}
}

// scamTypeBaseline returns a baseline risk for each scam type.
func scamTypeBaseline(scamType string) float64 {
	switch scamType {
	case "irs":
		return 0.85
	case "grandparent":
		return 0.8
	case "tech-support":
		return 0.75
	case "phishing":
		return 0.65
	case "spoofed":
		return 0.6
	case "romance":
		return 0.55
	case "medicare":
		return 0.5
	case "robocall":
		return 0.4
	default:
		return 0.3
	}
}

// saveModel writes the trained model data to disk.
func (t *Trainer) saveModel(result *TrainingResult) error {
	if err := os.MkdirAll(t.modelDir, 0755); err != nil {
		return fmt.Errorf("create model dir: %w", err)
	}

	weightsPath := filepath.Join(t.modelDir, "number_weights.json")

	weightsData := struct {
		Features []string  `json:"features"`
		Weights  []float64 `json:"weights"`
		Version  string    `json:"version"`
		Trained  time.Time `json:"trained_at"`
	}{
		Features: result.Features,
		Weights:  result.Weights,
		Version:  result.ModelVersion,
		Trained:  time.Now(),
	}

	data, err := json.MarshalIndent(weightsData, "", "  ")
	if err != nil {
		return fmt.Errorf("marshal weights: %w", err)
	}
	if err := os.WriteFile(weightsPath, data, 0644); err != nil {
		return fmt.Errorf("write weights: %w", err)
	}

	t.logger.Info("model weights saved", zap.String("path", weightsPath))
	return nil
}

// StartTrainingLoop runs model retraining on a schedule.
func (t *Trainer) StartTrainingLoop(ctx context.Context, interval time.Duration) {
	t.logger.Info("starting model training loop", zap.Duration("interval", interval))

	// Run initial training
	if _, err := t.TrainNumberModel(ctx); err != nil {
		t.logger.Warn("initial training failed", zap.Error(err))
	}

	ticker := time.NewTicker(interval)
	defer ticker.Stop()

	for {
		select {
		case <-ticker.C:
			t.logger.Info("scheduled model training starting")
			if _, err := t.TrainNumberModel(ctx); err != nil {
				t.logger.Warn("scheduled training failed", zap.Error(err))
			}
		case <-ctx.Done():
			t.logger.Info("training loop stopped")
			return
		}
	}
}
