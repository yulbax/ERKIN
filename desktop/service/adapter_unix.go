//go:build !windows

package main

import (
	"errors"
	"net"
	"net/netip"
	"time"
)

const releaseTimeout = 5 * time.Second

func releaseAdapter(name string, addresses []netip.Prefix) error {
	deadline := time.Now().Add(releaseTimeout)
	for {
		if _, err := net.InterfaceByName(name); err != nil {
			return nil
		}
		if time.Now().After(deadline) {
			return errors.New("interface " + name + " is still held by a previous session")
		}
		wake(addresses)
		time.Sleep(100 * time.Millisecond)
	}
}

func wake(addresses []netip.Prefix) {
	for _, prefix := range addresses {
		peer := prefix.Addr().Next()
		if !prefix.Contains(peer) {
			continue
		}
		conn, err := net.DialUDP("udp", nil, net.UDPAddrFromAddrPort(netip.AddrPortFrom(peer, 9)))
		if err != nil {
			continue
		}
		_, _ = conn.Write([]byte{0})
		_ = conn.Close()
	}
}
