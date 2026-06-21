package handler

import (
	"net/http"

	"go.uber.org/zap"

	"github.com/safering/backend/internal/ml"
	"github.com/safering/backend/internal/model"
)

// ModelHandler handles model-related endpoints:
//   - GET /v1/model — Query model info (type and version)
//   - GET /v1/model/latest — Get the latest active model
type ModelHandler struct {
	pipeline *ml.Pipeline
	logger   *zap.Logger
}

// NewModelHandler creates a new ModelHandler.
func NewModelHandler(pipeline *ml.Pipeline, logger *zap.Logger) *ModelHandler {
	return &ModelHandler{
		pipeline: pipeline,
		logger:   logger.Named("handler.model"),
	}
}

// ServeHTTP dispatches to the appropriate handler based on the URL path.
func (h *ModelHandler) ServeHTTP(w http.ResponseWriter, r *http.Request) {
	switch r.URL.Path {
	case "/v1/model":
		h.handleModel(w, r)
	case "/v1/model/latest":
		h.handleLatest(w, r)
	default:
		writeError(w, http.StatusNotFound, "endpoint not found")
	}
}

// handleModel handles GET /v1/model?type=number|sms&version={optional}
// Returns metadata about a specific model or the latest if no version specified.
func (h *ModelHandler) handleModel(w http.ResponseWriter, r *http.Request) {
	modelType := r.URL.Query().Get("type")
	if modelType == "" {
		modelType = "number"
	}
	if modelType != "number" && modelType != "sms" {
		writeError(w, http.StatusBadRequest, "invalid model type: must be 'number' or 'sms'")
		return
	}

	version := r.URL.Query().Get("version")

	var mv *model.ModelVersion
	var err error

	if version != "" {
		mv, err = h.pipeline.GetModelVersion(r.Context(), version, modelType)
	} else {
		mv, err = h.pipeline.GetLatestModel(r.Context(), modelType)
	}

	if err != nil {
		writeError(w, http.StatusNotFound, "model not found")
		return
	}

	writeJSON(w, http.StatusOK, model.ModelLatestResponse{
		Version:     mv.Version,
		DownloadURL: mv.DownloadURL,
		SHA256:      mv.SHA256,
		FileSize:    mv.FileSizeBytes,
		ModelType:   mv.ModelType,
		Accuracy:    mv.Accuracy,
	})
}

// handleLatest handles GET /v1/model/latest
// Returns the most recently deployed model version for both number and SMS types.
func (h *ModelHandler) handleLatest(w http.ResponseWriter, r *http.Request) {
	modelType := r.URL.Query().Get("type")
	if modelType == "" {
		modelType = "number"
	}
	if modelType != "number" && modelType != "sms" {
		writeError(w, http.StatusBadRequest, "invalid model type: must be 'number' or 'sms'")
		return
	}

	mv, err := h.pipeline.GetLatestModel(r.Context(), modelType)
	if err != nil {
		h.logger.Warn("no active model found",
			zap.String("type", modelType),
			zap.Error(err),
		)
		writeJSON(w, http.StatusOK, map[string]string{
			"status":  "no_model",
			"message": "No model has been deployed yet",
		})
		return
	}

	writeJSON(w, http.StatusOK, model.ModelLatestResponse{
		Version:     mv.Version,
		DownloadURL: mv.DownloadURL,
		SHA256:      mv.SHA256,
		FileSize:    mv.FileSizeBytes,
		ModelType:   mv.ModelType,
		Accuracy:    mv.Accuracy,
	})
}
