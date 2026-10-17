package main

import (
	"errors"
	"os"
	"path/filepath"
	"regexp"

	"github.com/yulbax/erkin/mihomocore"
)

const (
	ruleListDir     = "lists"
	legacyGeoIPName = "geoip.metadb"
)

var (
	ruleListKey      = regexp.MustCompile(`^[0-9a-f]{16}$`)
	ruleListFileName = regexp.MustCompile(`^[0-9a-f]{16}\.(domain|ipcidr)\.mrs$`)
)

func importRuleList(workDir string, key string, source string, format string, behavior string) (*mihomocore.RuleSet, error) {
	if !ruleListKey.MatchString(key) {
		return nil, errors.New("bad rule list key")
	}
	if !filepath.IsAbs(source) {
		return nil, errors.New("rule list path must be absolute")
	}
	return mihomocore.InstallRuleSet(source, filepath.Join(workDir, ruleListDir), key, format, behavior)
}

func removeRuleList(workDir string, name string) error {
	if !ruleListFileName.MatchString(name) {
		return errors.New("bad rule list name")
	}
	err := os.Remove(filepath.Join(workDir, ruleListDir, name))
	if errors.Is(err, os.ErrNotExist) {
		return nil
	}
	return err
}

func removeLegacyGeoIP(workDir string) {
	_ = os.Remove(filepath.Join(workDir, legacyGeoIPName))
}
