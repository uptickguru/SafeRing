package phoneintel

import (
	"strings"
	"sync"
)

// trieNode represents a node in the digit trie.
type trieNode struct {
	children [10]*trieNode
	payload  *PrefixEntry
}

// prefixTrie is the in-memory trie for prefix lookups.
type prefixTrie struct {
	root *trieNode
}

// Intelligence is the main phone number intelligence service.
// It is thread-safe for concurrent reads after Load() completes.
type Intelligence struct {
	trie *prefixTrie
	once sync.Once
}

// New creates a new Intelligence service. Call Load() before Lookup().
func New() *Intelligence {
	return &Intelligence{}
}

// Load populates the trie from the embedded prefix database.
// It is idempotent — calling Load() multiple times is safe.
func (i *Intelligence) Load() error {
	var loadErr error
	i.once.Do(func() {
		t := &prefixTrie{root: &trieNode{}}
		for idx := range prefixData {
			e := &prefixData[idx]
			t.insert(e)
		}
		i.trie = t
	})
	return loadErr
}

// Lookup performs an O(k) longest-prefix-match on the digit trie,
// where k is the length of the input prefix (typically ≤15 digits).
// The input should be digits only (E.164 without the leading +).
// Returns a zero-value NumberIntelligence if no prefix matches.
func (i *Intelligence) Lookup(digits string) NumberIntelligence {
	digits = strings.TrimPrefix(digits, "+")
	if i.trie == nil || len(digits) == 0 {
		return NumberIntelligence{}
	}

	best := i.trie.longestMatch(digits)
	if best == nil {
		return NumberIntelligence{}
	}

	return NumberIntelligence{
		Country:     best.Country,
		CountryName: best.CountryName,
		Region:      best.Region,
		City:        best.City,
		Carrier:     best.Carrier,
		LineType:    best.LineType,
		IsValid:     true,
		E164:        "+" + digits,
	}
}

// insert adds a prefix entry into the trie.
func (t *prefixTrie) insert(e *PrefixEntry) {
	node := t.root
	for _, ch := range e.Prefix {
		d := int(ch - '0')
		if d < 0 || d > 9 {
			return // skip non-digit characters
		}
		if node.children[d] == nil {
			node.children[d] = &trieNode{}
		}
		node = node.children[d]
	}
	node.payload = e
}

// longestMatch walks the trie digit-by-digit and returns the deepest
// payload found. This implements longest-prefix-match semantics:
// more specific (longer) prefixes always win over shorter ones.
func (t *prefixTrie) longestMatch(digits string) *PrefixEntry {
	var best *PrefixEntry
	node := t.root

	// Check root payload (empty prefix — unlikely but safe)
	if node.payload != nil {
		best = node.payload
	}

	for _, ch := range digits {
		d := int(ch - '0')
		if d < 0 || d > 9 {
			break
		}
		next := node.children[d]
		if next == nil {
			break
		}
		node = next
		if node.payload != nil {
			best = node.payload
		}
	}
	return best
}
