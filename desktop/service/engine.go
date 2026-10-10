package main

import (
	"context"
	"errors"
	"net/netip"
	"sync"
	"time"

	"github.com/sagernet/sing-box"
	"github.com/sagernet/sing-box/adapter"
	"github.com/sagernet/sing-box/common/urltest"
	C "github.com/sagernet/sing-box/constant"
	"github.com/sagernet/sing-box/experimental/clashapi"
	"github.com/sagernet/sing-box/experimental/deprecated"
	"github.com/sagernet/sing-box/include"
	"github.com/sagernet/sing-box/log"
	"github.com/sagernet/sing-box/option"
	"github.com/sagernet/sing-box/protocol/group"
	E "github.com/sagernet/sing/common/exceptions"
	"github.com/sagernet/sing/common/json"
	"github.com/sagernet/sing/service"
)

type instance struct {
	box      *box.Box
	ctx      context.Context
	cancel   context.CancelFunc
	adapters []tunDevice
}

type tunDevice struct {
	name      string
	addresses []netip.Prefix
}

type engine struct {
	mutex   sync.Mutex
	current *instance
}

func baseContext() context.Context {
	return include.Context(service.ContextWith(context.Background(), deprecated.NewStderrManager(log.StdLogger())))
}

func parse(ctx context.Context, content string) (option.Options, error) {
	options, err := json.UnmarshalExtendedContext[option.Options](ctx, []byte(content))
	if err != nil {
		return options, err
	}
	if options.Experimental == nil {
		options.Experimental = &option.ExperimentalOptions{}
	}
	if options.Experimental.ClashAPI == nil {
		options.Experimental.ClashAPI = &option.ClashAPIOptions{}
	}
	return options, nil
}

func create(content string, prepare func(option.Options) error) (*instance, error) {
	ctx, cancel := context.WithCancel(baseContext())
	options, err := parse(ctx, content)
	if err != nil {
		cancel()
		return nil, err
	}
	if prepare != nil {
		if err = prepare(options); err != nil {
			cancel()
			return nil, err
		}
	}
	created, err := box.New(box.Options{Context: ctx, Options: options})
	if err != nil {
		cancel()
		return nil, err
	}
	return &instance{box: created, ctx: ctx, cancel: cancel, adapters: tunAdapters(options)}, nil
}

func (i *instance) close() error {
	i.cancel()
	done := make(chan error, 1)
	go func() { done <- i.box.Close() }()
	select {
	case err := <-done:
		for _, tun := range i.adapters {
			_ = releaseAdapter(tun.name, tun.addresses)
		}
		return err
	case <-time.After(C.FatalStopTimeout):
		return errors.New("sing-box did not close in time")
	}
}

func tunAdapters(options option.Options) []tunDevice {
	var adapters []tunDevice
	for _, inbound := range options.Inbounds {
		tun, isTun := inbound.Options.(*option.TunInboundOptions)
		if !isTun || tun.InterfaceName == "" {
			continue
		}
		adapters = append(adapters, tunDevice{name: tun.InterfaceName, addresses: tun.Address})
	}
	return adapters
}

func releaseAdapters(options option.Options) error {
	for _, tun := range tunAdapters(options) {
		if err := releaseAdapter(tun.name, tun.addresses); err != nil {
			return E.Cause(err, "release adapter ", tun.name)
		}
	}
	return nil
}

func check(config string) error {
	checked, err := create(config, nil)
	if err != nil {
		return err
	}
	checked.cancel()
	return checked.box.Close()
}

func (e *engine) start(config string) error {
	e.mutex.Lock()
	defer e.mutex.Unlock()
	if e.current != nil {
		return errors.New("sing-box is already running")
	}
	if err := checkProcessLookup(); err != nil {
		return err
	}
	started, err := create(config, releaseAdapters)
	if err != nil {
		return err
	}
	if err = started.box.Start(); err != nil {
		_ = started.close()
		return err
	}
	e.current = started
	return nil
}

func (e *engine) stop() error {
	e.mutex.Lock()
	defer e.mutex.Unlock()
	if e.current == nil {
		return nil
	}
	stopping := e.current
	e.current = nil
	return stopping.close()
}

func (e *engine) running() *instance {
	e.mutex.Lock()
	defer e.mutex.Unlock()
	return e.current
}

func (e *engine) selectOutbound(groupTag string, outboundTag string) bool {
	active := e.running()
	if active == nil {
		return false
	}
	outbound, loaded := active.box.Outbound().Outbound(groupTag)
	if !loaded {
		return false
	}
	selector, isSelector := outbound.(*group.Selector)
	return isSelector && selector.SelectOutbound(outboundTag)
}

func (e *engine) delay(outboundTag string, link string, timeout time.Duration) int {
	active := e.running()
	if active == nil {
		return -1
	}
	outbound, loaded := active.box.Outbound().Outbound(outboundTag)
	if !loaded {
		return -1
	}
	ctx, cancel := context.WithTimeout(active.ctx, timeout)
	defer cancel()
	delay, err := urltest.URLTest(ctx, link, outbound)
	if err != nil || delay == 0 {
		return -1
	}
	return int(delay)
}

func (e *engine) traffic() (up int64, down int64, ok bool) {
	active := e.running()
	if active == nil {
		return 0, 0, false
	}
	server, isClash := service.FromContext[adapter.ClashServer](active.ctx).(*clashapi.Server)
	if !isClash {
		return 0, 0, false
	}
	up, down = server.TrafficManager().Total()
	return up, down, true
}
