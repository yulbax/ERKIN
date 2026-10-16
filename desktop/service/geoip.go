package main

import (
	"errors"
	"io"
	"os"
	"path/filepath"

	"github.com/metacubex/mihomo/component/mmdb"
	"github.com/yulbax/erkin/mihomocore"
)

const (
	geoIPFileName = "geoip.metadb"
	geoIPMaxBytes = 64 << 20
	geoIPFileMode = 0o644
)

var errNotGeoIP = errors.New("not a GeoIP database")

func importGeoIP(workDir string, source string) error {
	if !filepath.IsAbs(source) {
		return errors.New("GeoIP database path must be absolute")
	}
	info, err := os.Stat(source)
	if err != nil {
		return err
	}
	if !info.Mode().IsRegular() || info.Size() > geoIPMaxBytes || !mmdb.Verify(source) {
		return errNotGeoIP
	}
	target := filepath.Join(workDir, geoIPFileName)
	temp := target + ".import"
	if err := copyFile(source, temp); err != nil {
		_ = os.Remove(temp)
		return err
	}
	if !mmdb.Verify(temp) {
		_ = os.Remove(temp)
		return errNotGeoIP
	}
	if err := os.Rename(temp, target); err != nil {
		_ = os.Remove(temp)
		return errors.New("the GeoIP database is in use; disconnect the VPN and try again")
	}
	mihomocore.ReloadGeoIP()
	return nil
}

func copyFile(source string, target string) error {
	in, err := os.Open(source)
	if err != nil {
		return err
	}
	defer in.Close()
	out, err := os.OpenFile(target, os.O_CREATE|os.O_WRONLY|os.O_TRUNC, geoIPFileMode)
	if err != nil {
		return err
	}
	if _, err := io.Copy(out, io.LimitReader(in, geoIPMaxBytes)); err != nil {
		_ = out.Close()
		return err
	}
	return out.Close()
}
