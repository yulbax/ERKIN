package main

type hostProcess struct {
	PID     int    `json:"pid"`
	Parent  int    `json:"ppid"`
	Exe     string `json:"exe,omitempty"`
	Name    string `json:"name"`
	Unit    string `json:"unit,omitempty"`
	Flatpak string `json:"flatpak,omitempty"`
}
