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
	messages, ok := sockDiagDump(syscall.AF_INET, protocol, port, net.IPv4(127, 0, 0, 1).To4())
	return ok && len(messages) > 0
}

func sockDiagDump(family uint8, protocol uint8, port uint16, source []byte) ([]syscall.NetlinkMessage, bool) {
	socket, err := syscall.Socket(syscall.AF_NETLINK, syscall.SOCK_DGRAM, syscall.NETLINK_INET_DIAG)
	if err != nil {
		return nil, false
	}
	defer syscall.Close(socket)
	if err = syscall.Connect(socket, &syscall.SockaddrNetlink{Family: syscall.AF_NETLINK}); err != nil {
		return nil, false
	}
	request := make([]byte, sockDiagRequestSz)
	native := nativeEndian()
	native.PutUint32(request[0:4], sockDiagRequestSz)
	native.PutUint16(request[4:6], sockDiagByFamily)
	native.PutUint16(request[6:8], syscall.NLM_F_REQUEST|syscall.NLM_F_DUMP)
	request[16] = family
	request[17] = protocol
	native.PutUint32(request[20:24], 0xFFFFFFFF)
	binary.BigEndian.PutUint16(request[24:26], port)
	copy(request[28:44], source)
	native.PutUint64(request[64:72], 0xFFFFFFFFFFFFFFFF)
	if _, err = syscall.Write(socket, request); err != nil {
		return nil, false
	}
	var collected []syscall.NetlinkMessage
	response := make([]byte, 1<<16)
	for {
		n, err := syscall.Read(socket, response)
		if err != nil {
			return nil, false
		}
		messages, err := syscall.ParseNetlinkMessage(response[:n])
		if err != nil {
			return nil, false
		}
		for _, message := range messages {
			switch message.Header.Type {
			case syscall.NLMSG_DONE:
				return collected, true
			case syscall.NLMSG_ERROR:
				return nil, false
			}
			collected = append(collected, message)
		}
	}
}

func nativeEndian() binary.ByteOrder {
	var probe uint16 = 1
	if *(*byte)(unsafe.Pointer(&probe)) == 1 {
		return binary.LittleEndian
	}
	return binary.BigEndian
}
