package safecall

import (
	"encoding/json"
	"net/http"
	"sync"
	"time"
)

// Hub is a lightweight SSE fan-out (SignalR-class realtime without .NET).
// Clients: GET /v1/safecall/events?household_id=...
type Hub struct {
	mu   sync.Mutex
	subs map[string]map[chan []byte]struct{} // household -> set of chans
}

func NewHub() *Hub {
	return &Hub{subs: map[string]map[chan []byte]struct{}{}}
}

func (h *Hub) Subscribe(householdID string) chan []byte {
	ch := make(chan []byte, 16)
	h.mu.Lock()
	if h.subs[householdID] == nil {
		h.subs[householdID] = map[chan []byte]struct{}{}
	}
	h.subs[householdID][ch] = struct{}{}
	h.mu.Unlock()
	return ch
}

func (h *Hub) Unsubscribe(householdID string, ch chan []byte) {
	h.mu.Lock()
	if m := h.subs[householdID]; m != nil {
		delete(m, ch)
		if len(m) == 0 {
			delete(h.subs, householdID)
		}
	}
	h.mu.Unlock()
	close(ch)
}

func (h *Hub) Publish(householdID string, ev Event) {
	ev.At = time.Now().UTC()
	if ev.Household == "" {
		ev.Household = householdID
	}
	b, err := json.Marshal(ev)
	if err != nil {
		return
	}
	h.mu.Lock()
	defer h.mu.Unlock()
	for ch := range h.subs[householdID] {
		select {
		case ch <- b:
		default:
			// drop if slow client
		}
	}
	// also broadcast household "" global listeners? skip
}

// ServeSSE streams events for a household.
func (h *Hub) ServeSSE(w http.ResponseWriter, r *http.Request, householdID string) {
	flusher, ok := w.(http.Flusher)
	if !ok {
		http.Error(w, "sse not supported", http.StatusInternalServerError)
		return
	}
	w.Header().Set("Content-Type", "text/event-stream")
	w.Header().Set("Cache-Control", "no-cache")
	w.Header().Set("Connection", "keep-alive")
	w.WriteHeader(http.StatusOK)
	flusher.Flush()

	ch := h.Subscribe(householdID)
	defer h.Unsubscribe(householdID, ch)

	// hello
	hello, _ := json.Marshal(Event{Type: "connected", Household: householdID, At: time.Now().UTC()})
	_, _ = w.Write([]byte("data: " + string(hello) + "\n\n"))
	flusher.Flush()

	ctx := r.Context()
	for {
		select {
		case <-ctx.Done():
			return
		case msg, ok := <-ch:
			if !ok {
				return
			}
			_, _ = w.Write([]byte("data: "))
			_, _ = w.Write(msg)
			_, _ = w.Write([]byte("\n\n"))
			flusher.Flush()
		}
	}
}
