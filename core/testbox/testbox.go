package testbox

import (
	"context"
	"net"
	"sync"
	"time"

	"github.com/sagernet/sing-box"
	"github.com/sagernet/sing-box/adapter"
	"github.com/sagernet/sing-box/common/urltest"
	C "github.com/sagernet/sing-box/constant"
	"github.com/sagernet/sing-box/include"
	"github.com/sagernet/sing-box/option"
	"github.com/sagernet/sing/common/json"
	M "github.com/sagernet/sing/common/metadata"
	N "github.com/sagernet/sing/common/network"
	"github.com/sagernet/sing/service"
)

const concurrency = 10

func URLTest(config string, link string, timeoutMs int32, defaultInterface string) (string, error) {
	ctx, cancel := context.WithCancel(include.Context(context.Background()))
	defer cancel()
	options, err := json.UnmarshalExtendedContext[option.Options](ctx, []byte(config))
	if err != nil {
		return "", err
	}
	options.Log = &option.LogOptions{Disabled: true}
	if defaultInterface != "" {
		if options.Route == nil {
			options.Route = &option.RouteOptions{}
		}
		options.Route.DefaultInterface = defaultInterface
	}
	instance, err := box.New(box.Options{Context: ctx, Options: options})
	if err != nil {
		return "", err
	}
	defer instance.Close()
	if err = instance.Start(); err != nil {
		return "", err
	}
	dnsRouter := service.FromContext[adapter.DNSRouter](ctx)
	targets := make(map[string]N.Dialer)
	for _, outbound := range options.Outbounds {
		if dialer, loaded := instance.Outbound().Outbound(outbound.Tag); loaded {
			targets[outbound.Tag] = dialer
		}
	}
	for _, endpoint := range options.Endpoints {
		if dialer, loaded := instance.Outbound().Outbound(endpoint.Tag); loaded {
			targets[endpoint.Tag] = &resolvingDialer{Dialer: dialer, dnsRouter: dnsRouter}
		}
	}
	timeout := time.Duration(timeoutMs) * time.Millisecond
	results := make(map[string]int, len(targets))
	var access sync.Mutex
	var group sync.WaitGroup
	slots := make(chan struct{}, concurrency)
	for tag, dialer := range targets {
		group.Add(1)
		go func() {
			defer group.Done()
			slots <- struct{}{}
			defer func() { <-slots }()
			testCtx, testCancel := context.WithTimeout(ctx, timeout)
			defer testCancel()
			delay, testErr := urltest.URLTest(testCtx, link, dialer)
			result := int(delay)
			if testErr != nil || delay == 0 {
				result = -1
			}
			access.Lock()
			results[tag] = result
			access.Unlock()
		}()
	}
	group.Wait()
	encoded, err := json.Marshal(results)
	if err != nil {
		return "", err
	}
	return string(encoded), nil
}

type resolvingDialer struct {
	N.Dialer
	dnsRouter adapter.DNSRouter
}

func (d *resolvingDialer) DialContext(ctx context.Context, network string, destination M.Socksaddr) (net.Conn, error) {
	if !destination.IsFqdn() {
		return d.Dialer.DialContext(ctx, network, destination)
	}
	addresses, err := d.dnsRouter.Lookup(ctx, destination.Fqdn, adapter.DNSQueryOptions{Strategy: C.DomainStrategyPreferIPv4})
	if err != nil {
		return nil, err
	}
	return N.DialSerial(ctx, d.Dialer, network, destination, addresses)
}
