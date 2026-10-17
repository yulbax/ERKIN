package main

import (
	"errors"
	"os"
	"path/filepath"
	"regexp"
)

const (
	ruleListDir      = "lists"
	ruleListMaxBytes = 64 << 20
)

var ruleListName = regexp.MustCompile(`^[0-9a-f]{16}\.(domain|ipcidr)\.txt$`)

func importRuleList(workDir string, name string, source string) error {
	if !ruleListName.MatchString(name) {
		return errors.New("bad rule list name")
	}
	dir := filepath.Join(workDir, ruleListDir)
	target := filepath.Join(dir, name)
	if source == "" {
		if err := os.Remove(target); err != nil && !errors.Is(err, os.ErrNotExist) {
			return err
		}
		return nil
	}
	if !filepath.IsAbs(source) {
		return errors.New("rule list path must be absolute")
	}
	info, err := os.Stat(source)
	if err != nil {
		return err
	}
	if !info.Mode().IsRegular() || info.Size() > ruleListMaxBytes {
		return errors.New("not a rule list")
	}
	if err := os.MkdirAll(dir, 0o755); err != nil {
		return err
	}
	temp := target + ".import"
	if err := copyFile(source, temp); err != nil {
		_ = os.Remove(temp)
		return err
	}
	if err := os.Rename(temp, target); err != nil {
		_ = os.Remove(temp)
		return err
	}
	return nil
}
