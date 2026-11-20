package mihomocore

import (
	"testing"

	C "github.com/metacubex/mihomo/constant"
	R "github.com/metacubex/mihomo/rules"
)

func TestAProgramReplacedWhileRunningMatchesItsDeletedPathInsideALogicRule(t *testing.T) {
	rule, err := R.ParseRule("AND", "((OR,((PROCESS-PATH,/opt/zen/zen-bin),(PROCESS-PATH,/opt/zen/zen-bin (deleted)))),(DOMAIN-SUFFIX,example.org))", "DIRECT", nil, nil)
	if err != nil {
		t.Fatal(err)
	}
	for path, want := range map[string]bool{
		"/opt/zen/zen-bin":           true,
		"/opt/zen/zen-bin (deleted)": true,
		"/opt/zen/zen-bin-helper":    false,
	} {
		metadata := &C.Metadata{Host: "www.example.org", ProcessPath: path}
		if matched, _ := rule.Match(metadata, C.RuleMatchHelper{}); matched != want {
			t.Fatalf("%q matched=%v, want %v", path, matched, want)
		}
	}
}

func TestAPlainRuleMatchesADeletedPathAndName(t *testing.T) {
	path, err := R.ParseRule("PROCESS-PATH", "/opt/zen/zen-bin (deleted)", "p1", nil, nil)
	if err != nil {
		t.Fatal(err)
	}
	name, err := R.ParseRule("PROCESS-NAME", "ciadpi (deleted)", "DIRECT", nil, nil)
	if err != nil {
		t.Fatal(err)
	}
	metadata := &C.Metadata{ProcessPath: "/opt/zen/zen-bin (deleted)", Process: "ciadpi (deleted)"}
	if matched, _ := path.Match(metadata, C.RuleMatchHelper{}); !matched {
		t.Fatal("the path rule should match the deleted path")
	}
	if matched, _ := name.Match(metadata, C.RuleMatchHelper{}); !matched {
		t.Fatal("the name rule should match the deleted name")
	}
}
