package main

import (
	"errors"
	"net"

	"golang.org/x/sys/unix"
)

func protect(conn *net.UnixConn, interfaceIndex func() (int, error)) error {
	oob := make([]byte, unix.CmsgSpace(4))
	_, oobn, _, _, err := conn.ReadMsgUnix(make([]byte, 1), oob)
	if err != nil {
		return err
	}
	messages, err := unix.ParseSocketControlMessage(oob[:oobn])
	if err != nil {
		return err
	}
	var fds []int
	for _, message := range messages {
		if received, parseErr := unix.ParseUnixRights(&message); parseErr == nil {
			fds = append(fds, received...)
		}
	}
	defer func() {
		for _, fd := range fds {
			_ = unix.Close(fd)
		}
	}()
	if len(fds) != 1 {
		return errors.New("expected exactly one socket")
	}
	index, err := interfaceIndex()
	if err != nil {
		return err
	}
	if err = unix.SetsockoptInt(fds[0], unix.SOL_SOCKET, unix.SO_BINDTOIFINDEX, index); err != nil {
		return err
	}
	_, err = conn.Write([]byte{1})
	return err
}
