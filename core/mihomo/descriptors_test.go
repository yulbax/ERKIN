package mihomocore

import (
	"encoding/json"
	"os"
	"testing"

	"github.com/metacubex/mihomo/adapter"
)

func TestCoreAcceptsEveryGoldenDescriptor(t *testing.T) {
	data, err := os.ReadFile("../../shared/src/desktopTest/resources/core-descriptors.json")
	if err != nil {
		t.Fatal(err)
	}
	var entries []struct {
		Wire  string         `json:"wire"`
		Proxy map[string]any `json:"proxy"`
	}
	if err := json.Unmarshal(data, &entries); err != nil {
		t.Fatal(err)
	}
	if len(entries) == 0 {
		t.Fatal("golden file has no descriptors")
	}
	for _, entry := range entries {
		t.Run(entry.Wire, func(t *testing.T) {
			entry.Proxy["name"] = entry.Wire
			if _, err := adapter.ParseProxy(entry.Proxy); err != nil {
				t.Fatal(err)
			}
		})
	}
}
