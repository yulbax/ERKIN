//go:build !linux

package main

func openTunnel(config string) (string, tunnelSession, error) {
	return config, nil, nil
}
