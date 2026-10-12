package main

import (
	"encoding/binary"
	"net"
)

func protect(conn *net.UnixConn, interfaceIndex func() (int, error)) error {
	index, err := interfaceIndex()
	if err != nil {
		return err
	}
	_, err = conn.Write(binary.LittleEndian.AppendUint32(nil, uint32(index)))
	return err
}
