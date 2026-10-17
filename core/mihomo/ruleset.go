package mihomocore

import (
	"bytes"
	"encoding/binary"
	"errors"
	"io"
	"os"
	"path/filepath"

	P "github.com/metacubex/mihomo/constant/provider"
	"github.com/metacubex/mihomo/rules/provider"

	"github.com/klauspost/compress/zstd"
)

const (
	ruleSetMaxBytes = 64 << 20
	ruleSetMaxRaw   = 512 << 20
	ruleSetFileMode = 0o644
	ruleSetDirMode  = 0o755
	ruleSetText     = "text"
	ruleSetMrs      = "mrs"
	ruleSetSuffix   = ".mrs"
)

var (
	errRuleSetCorrupt   = errors.New("corrupt")
	errRuleSetWrongKind = errors.New("wrong-kind")
	errRuleSetEmpty     = errors.New("empty")
	errRuleSetTooLarge  = errors.New("too-large")
	errRuleSetNotAList  = errors.New("not-a-list")
)

type RuleSet struct {
	Behavior string
	Count    int64
}

func InstallRuleSet(source string, dir string, key string, format string, behavior string) (*RuleSet, error) {
	data, err := readRuleSetSource(source)
	if err != nil {
		return nil, err
	}
	var encoded []byte
	switch format {
	case ruleSetText:
		parsed, err := ruleBehavior(behavior)
		if err != nil {
			return nil, err
		}
		encoded, err = convertRuleSet(data, parsed)
		if err != nil {
			return nil, err
		}
	case ruleSetMrs:
		encoded = data
	default:
		return nil, errRuleSetNotAList
	}
	set, err := checkRuleSet(encoded, behavior)
	if err != nil {
		return nil, err
	}
	if err := os.MkdirAll(dir, ruleSetDirMode); err != nil {
		return nil, err
	}
	target := filepath.Join(dir, key+"."+set.Behavior+ruleSetSuffix)
	temp := target + ".import"
	if err := os.WriteFile(temp, encoded, ruleSetFileMode); err != nil {
		_ = os.Remove(temp)
		return nil, err
	}
	if err := os.Rename(temp, target); err != nil {
		_ = os.Remove(temp)
		return nil, err
	}
	return set, nil
}

func readRuleSetSource(source string) ([]byte, error) {
	info, err := os.Stat(source)
	if err != nil {
		return nil, err
	}
	if !info.Mode().IsRegular() {
		return nil, errRuleSetNotAList
	}
	if info.Size() > ruleSetMaxBytes {
		return nil, errRuleSetTooLarge
	}
	return os.ReadFile(source)
}

func convertRuleSet(data []byte, behavior P.RuleBehavior) ([]byte, error) {
	var out bytes.Buffer
	if err := provider.ConvertToMrs(data, behavior, P.TextRule, &out); err != nil {
		return nil, errRuleSetEmpty
	}
	return out.Bytes(), nil
}

func checkRuleSet(data []byte, expected string) (*RuleSet, error) {
	reader, err := zstd.NewReader(bytes.NewReader(data))
	if err != nil {
		return nil, errRuleSetCorrupt
	}
	defer reader.Close()
	var header [len(provider.MrsMagicBytes) + 1]byte
	if _, err := io.ReadFull(reader, header[:]); err != nil || [4]byte(header[:4]) != provider.MrsMagicBytes {
		return nil, errRuleSetCorrupt
	}
	var count int64
	if err := binary.Read(reader, binary.BigEndian, &count); err != nil {
		return nil, errRuleSetCorrupt
	}
	read, err := io.Copy(io.Discard, io.LimitReader(reader, ruleSetMaxRaw+1))
	if err != nil {
		return nil, errRuleSetCorrupt
	}
	if read > ruleSetMaxRaw {
		return nil, errRuleSetTooLarge
	}
	behavior, err := behaviorOfByte(header[4])
	if err != nil {
		return nil, err
	}
	if expected != "" && expected != behaviorName(behavior) {
		return nil, errRuleSetWrongKind
	}
	if count <= 0 {
		return nil, errRuleSetEmpty
	}
	if err := provider.ConvertToMrs(data, behavior, P.MrsRule, io.Discard); err != nil {
		return nil, errRuleSetCorrupt
	}
	return &RuleSet{Behavior: behaviorName(behavior), Count: count}, nil
}

func ruleBehavior(name string) (P.RuleBehavior, error) {
	for _, behavior := range []P.RuleBehavior{P.Domain, P.IPCIDR} {
		if behaviorName(behavior) == name {
			return behavior, nil
		}
	}
	return 0, errRuleSetWrongKind
}

func behaviorOfByte(value byte) (P.RuleBehavior, error) {
	for _, behavior := range []P.RuleBehavior{P.Domain, P.IPCIDR} {
		if behavior.Byte() == value {
			return behavior, nil
		}
	}
	return 0, errRuleSetWrongKind
}

func behaviorName(behavior P.RuleBehavior) string {
	switch behavior {
	case P.IPCIDR:
		return "ipcidr"
	default:
		return "domain"
	}
}
