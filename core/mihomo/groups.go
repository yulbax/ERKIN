package mihomocore

import (
	"github.com/metacubex/mihomo/adapter/outboundgroup"
	"github.com/metacubex/mihomo/tunnel"
)

type GroupHealth struct {
	Alive int32
	Total int32
}

func GroupHealthOf(name string, url string) *GroupHealth {
	proxy, found := tunnel.Proxies()[name]
	if !found {
		return nil
	}
	group, isGroup := proxy.Adapter().(outboundgroup.ProxyGroup)
	if !isGroup {
		return nil
	}
	health := &GroupHealth{}
	for _, member := range group.Proxies() {
		state, tested := member.ExtraDelayHistories()[url]
		if !tested {
			return nil
		}
		health.Total++
		if state.Alive {
			health.Alive++
		}
	}
	return health
}
