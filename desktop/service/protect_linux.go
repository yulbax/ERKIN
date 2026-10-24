package main

import "golang.org/x/sys/unix"

func bindToInterface(fd int, index int) error {
	return unix.SetsockoptInt(fd, unix.SOL_SOCKET, unix.SO_BINDTOIFINDEX, index)
}
