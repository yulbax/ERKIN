package main

import (
	"encoding/binary"
	"errors"
	"net"
	"os/exec"
	"syscall"
	"unsafe"
)

const (
	sockDiagByFamily  = 20
	sockDiagRequestSz = syscall.SizeofNlMsghdr + 56
	processLookupHelp = "process-lookup-unavailable: Per-app routing is unavailable: the kernel cannot report which program owns a connection " +
		"(the inet_diag, tcp_diag and udp_diag kernel modules are not loaded). This usually happens after a kernel " +
		"update — reboot the computer. Otherwise make sure your kernel provides these modules."
)

func checkProcessLookup() error {
	if processLookupWorks() {
		return nil
	}
	_ = exec.Command("modprobe", "-a", "inet_diag", "tcp_diag", "udp_diag").Run()
	if processLookupWorks() {
		return nil
	}
	return errors.New(processLookupHelp)
}

func processLookupWorks() bool {
	tcp, err := net.Listen("tcp4", "127.0.0.1:0")
	if err != nil {
		return false
	}
	defer tcp.Close()
	udp, err := net.ListenPacket("udp4", "127.0.0.1:0")
	if err != nil {
		return false
	}
	defer udp.Close()
	return sockDiagAnswers(syscall.IPPROTO_TCP, uint16(tcp.Addr().(*net.TCPAddr).Port)) &&
		sockDiagAnswers(syscall.IPPROTO_UDP, uint16(udp.LocalAddr().(*net.UDPAddr).Port))
}

func sockDiagAnswers(protocol uint8, port uint16) bool {
	socket, err := syscall.Socket(syscall.AF_NETLINK, syscall.SOCK_DGRAM, syscall.NETLINK_INET_DIAG)
	if err != nil {
		return false
	}
	defer syscall.Close(socket)
	if err = syscall.Connect(socket, &syscall.SockaddrNetlink{Family: syscall.AF_NETLINK}); err != nil {
		return false
	}
	request := make([]byte, sockDiagRequestSz)
	native := nativeEndian()
	native.PutUint32(request[0:4], sockDiagRequestSz)
	native.PutUint16(request[4:6], sockDiagByFamily)
	native.PutUint16(request[6:8], syscall.NLM_F_REQUEST|syscall.NLM_F_DUMP)
	request[16] = syscall.AF_INET
	request[17] = protocol
	native.PutUint32(request[20:24], 0xFFFFFFFF)
	binary.BigEndian.PutUint16(request[24:26], port)
	copy(request[28:32], net.IPv4(127, 0, 0, 1).To4())
	native.PutUint64(request[64:72], 0xFFFFFFFFFFFFFFFF)
	if _, err = syscall.Write(socket, request); err != nil {
		return false
	}
	response := make([]byte, 1<<16)
	n, err := syscall.Read(socket, response)
	if err != nil {
		return false
	}
	messages, err := syscall.ParseNetlinkMessage(response[:n])
	if err != nil || len(messages) == 0 {
		return false
	}
	return messages[0].Header.Type != syscall.NLMSG_ERROR
}

func nativeEndian() binary.ByteOrder {
	var probe uint16 = 1
	if *(*byte)(unsafe.Pointer(&probe)) == 1 {
		return binary.LittleEndian
	}
	return binary.BigEndian
}
