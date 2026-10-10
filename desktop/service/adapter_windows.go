package main

import (
	"crypto/md5"
	"errors"
	"net/netip"
	"strings"
	"unsafe"

	"golang.org/x/sys/windows"
	"golang.org/x/sys/windows/registry"
)

var netClassGUID = windows.GUID{Data1: 0x4d36e972, Data2: 0xe325, Data3: 0x11ce, Data4: [8]byte{0xbf, 0xc1, 0x08, 0x00, 0x2b, 0xe1, 0x03, 0x18}}

func wintunGUID(name string) windows.GUID {
	sum := md5.Sum([]byte("wintun" + name))
	return *(*windows.GUID)(unsafe.Pointer(&sum[0]))
}

func releaseAdapter(name string, _ []netip.Prefix) error {
	_, err := removeStaleAdapter(name)
	return err
}

func removeStaleAdapter(name string) (int, error) {
	target := wintunGUID(name).String()
	devices, err := windows.SetupDiGetClassDevsEx(&netClassGUID, "", 0, 0, 0, "")
	if err != nil {
		return 0, err
	}
	defer devices.Close()
	removed := 0
	for index := 0; ; index++ {
		device, err := devices.EnumDeviceInfo(index)
		if errors.Is(err, windows.ERROR_NO_MORE_ITEMS) {
			break
		}
		if err != nil {
			continue
		}
		if !strings.EqualFold(instanceID(devices, device), target) {
			continue
		}
		params := windows.RemoveDeviceParams{
			ClassInstallHeader: *windows.MakeClassInstallHeader(windows.DIF_REMOVE),
			Scope:              windows.DI_REMOVEDEVICE_GLOBAL,
		}
		err = devices.SetClassInstallParams(device, &params.ClassInstallHeader, uint32(unsafe.Sizeof(params)))
		if err == nil {
			err = devices.CallClassInstaller(windows.DIF_REMOVE, device)
		}
		if err != nil {
			return removed, err
		}
		removed++
	}
	return removed, nil
}

func instanceID(devices windows.DevInfo, device *windows.DevInfoData) string {
	key, err := devices.OpenDevRegKey(device, windows.DICS_FLAG_GLOBAL, 0, windows.DIREG_DRV, windows.KEY_QUERY_VALUE)
	if err != nil {
		return ""
	}
	defer registry.Key(key).Close()
	value, _, err := registry.Key(key).GetStringValue("NetCfgInstanceId")
	if err != nil {
		return ""
	}
	return value
}
