//go:build !darwin

package main

func pinSystemDNS(string) error { return nil }

func restoreSystemDNS(string) error { return nil }
