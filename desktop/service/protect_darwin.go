package main

import "golang.org/x/sys/unix"

func bindToInterface(fd int, index int) error {
	address, err := unix.Getsockname(fd)
	if err != nil {
		return err
	}
	if _, ipv6 := address.(*unix.SockaddrInet6); ipv6 {
		return unix.SetsockoptInt(fd, unix.IPPROTO_IPV6, unix.IPV6_BOUND_IF, index)
	}
	return unix.SetsockoptInt(fd, unix.IPPROTO_IP, unix.IP_BOUND_IF, index)
}
